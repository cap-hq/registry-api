// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.config.CacheConfig;
import com.cvshealth.cap.registry.rollup.config.RollupExecutorConfig;
import com.cvshealth.cap.registry.rollup.mapper.UserMapper;
import com.cvshealth.cap.registry.rollup.model.*;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

@Slf4j
@Service
public class RollupService {
    @Autowired
    private ManagerRankingUtil managerRankingUtil;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AzureAdUserService azureAdUserService;
    @Autowired
    @Qualifier(RollupExecutorConfig.USER_EXECUTOR_BEAN_NAME)
    private Executor userExecutor;
    @Autowired
    private CacheManager cacheManager;
    @Value("${app.metrics.prefix}")
    private String metricPrefix;

    private static final String METRIC_ROLLUP = "rollup";
    private static final String METRIC_MANAGER_NO_MANAGER = METRIC_ROLLUP + ".manager.no_manager";
    private static final String METRIC_MANAGER_NO_MANAGER_DESCRIPTION = "Count of users with no manager in Azure AD, by source path (team or member).";
    private static final String METRIC_MANAGER_TIE = METRIC_ROLLUP + ".manager.tied";
    private static final String METRIC_MANAGER_TIE_DESCRIPTION = "Count the number of teams ambiguous managers.";
    private static final List<TeamMemberRole> MEMBER_ROLES = List.of(TeamMemberRole.OWNER, TeamMemberRole.MEMBER);

    private final Meter meter =
            GlobalOpenTelemetry.meterBuilder("io.opentelemetry.metrics.registry.rollup")
                    .build();
    private LongCounter noManagerCount;
    private LongCounter tiedManagerCount;

    @PostConstruct
    public void createMetrics() {
        noManagerCount = meter
                .counterBuilder(metricPrefix + "." + METRIC_MANAGER_NO_MANAGER)
                .setDescription(METRIC_MANAGER_NO_MANAGER_DESCRIPTION)
                .build();
        tiedManagerCount = meter
                .counterBuilder(metricPrefix + "." + METRIC_MANAGER_TIE)
                .setDescription(METRIC_MANAGER_TIE_DESCRIPTION)
                .build();
    }

    @Cacheable(value = CacheConfig.CACHE_TEAM, key = "#team.name")
    public List<UserView> getTeamManagers(TeamView team) {
        return computeTeamManagers(team);
    }

    public List<UserView> refreshTeamManagersCache(TeamView team) {
        List<UserView> teamManagers = computeTeamManagers(team);
        Cache teamRollupCache = cacheManager.getCache(CacheConfig.CACHE_TEAM);
        if (teamRollupCache != null) {
            teamRollupCache.put(team.getName(), teamManagers);
            log.debug("refreshTeamManagersCache for {}. Cache updated", team.getName());
        }
        return teamManagers;
    }

    private List<UserView> computeTeamManagers(TeamView team) {
        log.debug("teamManagers for {}", team.getName());
        List<User> teamMembers = getUsers(team);
        if (CollectionUtils.isEmpty(teamMembers)) {
            return Collections.emptyList();
        }

        List<User> membersWithManager = teamMembers.stream()
                .filter(u -> {
                    if (u.getManager() == null) {
                        log.warn("No manager found for user {} ({}), excluding from team rollup",
                                u.getExternalId(), u.getExternalAlias());
                        noManagerCount.add(1, Attributes.of(stringKey("source"), "team"));
                        return false;
                    }
                    return true;
                })
                .toList();

        if (membersWithManager.isEmpty()) {
            return Collections.emptyList();
        }


        // find manager for each team member and ranK them based on occurrence and team member role
        List<Pair<User, Long>> managerRoleWeights = new ArrayList<>();

        Map<String, String> ownerMemberMap = new HashMap<>();

        // Then process the regular manager hierarchy logic
        membersWithManager.forEach(u -> {
            if (u != null) {
                Long roleWeight = team.getMembers().stream().anyMatch(m -> m.getExternalId().equals(u.getExternalId())
                        && TeamMemberRole.OWNER.equals(m.getRole())) ? 2L : 1L;
                User userManager = u.getManager();
                // teamMember is an owner
                if(roleWeight == 2L) {
                        ownerMemberMap.put(u.getExternalId(), userManager.getExternalId());
                }
                Pair<User, Long> managerRanking = managerRoleWeights.stream()
                        .filter(pair -> pair.getKey().getExternalId().equals(userManager.getExternalId()))
                        .findFirst().orElse(null);
                if (managerRanking != null) {
                    managerRanking.setValue(managerRanking.getValue() + roleWeight);
                } else {
                    managerRoleWeights.add(new MutablePair<>(userManager, roleWeight));
                }
            }
        });

        // adjusts the managerRanking map to add weight to managers who are owners of members
        if(!managerRoleWeights.isEmpty()) this.adjustManagerRankingIfOwnerIsManagerOfAnotherMember(managerRoleWeights, ownerMemberMap);

        // adjusts the managerRanking map to resolve tiebreaker scenario
        if(!managerRoleWeights.isEmpty() && this.hasTiedRankings(managerRoleWeights)) this.resolveManagerTie(managerRoleWeights);

        return managerRoleWeights.isEmpty() ? Collections.emptyList() : selectManager(managerRoleWeights, team);
    }

    private @NonNull List<User> getUsers(TeamView team) {
        if (team.getMembers().isEmpty()) {
            return Collections.emptyList();
        }
        // get team members with manager hierarchy from azure AD (executor queue provides backpressure)
        List<TeamMemberView> members = team.getMembers().stream()
                .filter(m -> MEMBER_ROLES.contains(m.getRole()))
                .toList();

        List<CompletableFuture<User>> futures = members.stream()
                .map(m -> CompletableFuture.supplyAsync(
                        () -> azureAdUserService.findById(m.getExternalId()).orElse(null),
                        userExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
    }

    /**
     * Checks if there are multiple managers with the same highest rank
     * @param managerRankings List of manager ranKs
     * @return true if there are tied ranKs, false otherwise
     */
    private boolean hasTiedRankings(List<Pair<User, Long>> managerRankings) {
        if (managerRankings.size() <= 1) {
            return false;
        }

        // Find the highest ranK
        Long highestRank = managerRankings.stream()
                .mapToLong(Pair::getValue)
                .max()
                .orElse(0L);

        // Count how many managers have the highest ranK
        long countWithHighestRank = managerRankings.stream()
                .mapToLong(Pair::getValue)
                .filter(ranK -> ranK == (highestRank))
                .count();

        // Return true if more than one manager has the highest ranK
        return countWithHighestRank > 1;
    }

    @Cacheable(value = CacheConfig.CACHE_MEMBER, key = "#member.externalId")
    public List<UserView> getMemberManagers(MemberView member) {
        // get member with manager hierarchy from azure AD
        User user = azureAdUserService.findById(member.getExternalId()).orElse(null);
        if (user != null) {
            if (user.getManager() != null) {
                return getFlatManagerHierarchy(user.getManager());
            }
            log.warn("No manager found for user {} ({})", user.getExternalId(), user.getExternalAlias());
            noManagerCount.add(1, Attributes.of(stringKey("source"), "member"));
        }
        return Collections.emptyList();
    }

    private List<UserView> selectManager(List<Pair<User, Long>> managerRankings, TeamView team) {
        // sort manager ranKs by weight in descending order, then by externalAlias
        managerRankings.sort(Comparator.comparing((Pair<User, Long> p) -> -p.getValue())
                .thenComparing(p -> p.getKey().getExternalAlias()));
        Pair<User, Long> managerRank = managerRankings.get(0);
        logSelectedManager(managerRank, managerRankings, team);
        return getFlatManagerHierarchy(managerRank.getKey());
    }

    List<UserView> getFlatManagerHierarchy(User manager) {
        List<UserView> managers = new ArrayList<>();
        User currentManager = manager;
        while (currentManager != null) {
            managers.add(userMapper.toUserViewDto(currentManager));
            currentManager = currentManager.getManager();
        }
        return managers;
    }

    private void logSelectedManager(Pair<User, Long> managerRank, List<Pair<User, Long>> managerRanks, TeamView team) {
        log.info(String.format("Selecting '%s' for team '%s' (%s)",
                managerRank.getKey().getDisplayName(), team.getName(), formatManagerRanks(managerRanks)));
        List<User> tiedManagers = managerRanks.stream()
                .filter(pair -> !pair.getKey().getExternalId().equals(managerRank.getKey().getExternalId()) &&
                        pair.getValue() == managerRank.getValue()).map(Pair::getKey).toList();
        if (!tiedManagers.isEmpty()) {
            tiedManagerCount.add(1, getTiedManagerAttributes(managerRank.getKey(), tiedManagers, team));
        }
    }

    private Attributes getTiedManagerAttributes(User key, List<User> tiedManagers, TeamView team) {
        return Attributes.of(stringKey("externalId"), key.getExternalId(),
                stringKey("team"), team.getName(),
                stringKey("externalAlias"), key.getExternalAlias(),
                stringKey("displayName"), key.getDisplayName(),
                stringKey("tiedExternalAliases"), tiedManagers.stream().map(User::getExternalAlias)
                        .collect(Collectors.joining(";")),
                stringKey("tiedDisplayNames"), tiedManagers.stream().map(User::getDisplayName)
                        .collect(Collectors.joining(";"))
        );
    }

    private String formatManagerRanks(List<Pair<User, Long>> managerRankins) {
        return managerRankins.stream().map(ms -> String.format("%s :: %s = %s",
                ms.getKey().getDisplayName(), ms.getKey().getTitle(),
                ms.getValue())).collect(Collectors.joining("; "));
    }

    /**
     * Increases the weight on the lowerRankedManager by Job title in the case of a tie
     * @param managerRanks List of manager ranKs
     * @return void
     */
    private void resolveManagerTie(List<Pair<User, Long>> managerRanks) {

        String lowestRankedManagerExternalId = managerRankingUtil.getLowestRankedManagerExternalId(managerRanks);

        for(Pair<User, Long> managerRank : managerRanks) {
            // adds weight to the lowest ranked Manager
            if(lowestRankedManagerExternalId.equals(managerRank.getKey().getExternalId())) {
                managerRank.setValue(managerRank.getValue() + 1L);
            }
        }

    }


    private void adjustManagerRankingIfOwnerIsManagerOfAnotherMember(List<Pair<User, Long>> managerRanks, Map<String, String> ownerMemberMap) {

        managerRanks.forEach(managerRank -> {
                    if(ownerMemberMap.containsKey(managerRank.getKey().getExternalId())) {
                managerRank.setValue(managerRank.getValue() + 1L);
            }

        });
    }

}
