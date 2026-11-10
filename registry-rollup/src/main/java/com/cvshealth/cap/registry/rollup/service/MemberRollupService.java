// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.mapper.MapperDecoratorProvider;
import com.cvshealth.cap.registry.rollup.mapper.MemberMapper;
import com.cvshealth.cap.registry.rollup.mapper.UserMapper;
import com.cvshealth.cap.registry.rollup.model.*;
import com.cvshealth.cap.registry.rollup.model.exception.BadRequestException;
import com.cvshealth.cap.registry.rollup.model.exception.ExceptionReason;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.rollup.model.exception.ExceptionReason.*;

@Service
@Slf4j
public class MemberRollupService extends AbstractRollupService {
    private final MemberService memberService;
    private final MemberMapper mapper;
    private final UserMapper userMapper;
    private final RollupService rollupService;
    private final AzureAdUserService azureAdUserService;

    private static final int PAGE_NUMBER_AD_LIMIT = 1;
    private static final int PAGE_SIZE_COLLECTION_SEARCH_LIMIT = 1;

    public MemberRollupService(MemberService memberService, MemberMapper mapper,
                               UserMapper userMapper, RollupService rollupService,
                               AzureAdUserService azureAdUserService,
                               List<MapperDecoratorProvider> mapperDecoratorProviders) {
        super(mapperDecoratorProviders);
        this.memberService = memberService;
        this.userMapper = userMapper;
        this.rollupService = rollupService;
        this.azureAdUserService = azureAdUserService;
        this.mapper = mapper;
    }

    public PaginatedMemberRollupResponse getMemberRollup(PaginationInput paginationInput, MemberSearchFilter filter) {
        log.debug("memberRollup for filter[{}]", filter);
        return filter == null || Optional.ofNullable(filter.getAppRegistryMembersOnly()).orElse(true) ?
                getMemberRollupFromRegistry(paginationInput, filter) : getMemberRollupFromUser(paginationInput, filter);
    }

    PaginatedMemberRollupResponse getMemberRollupFromRegistry(PaginationInput paginationInput, MemberSearchFilter filter) {
        PaginatedMemberView paginatedView = memberService.getMembers(paginationInput, filter);
        List<MemberRollupView> memberRollupView = paginatedView.getMembers().parallelStream().map(this::createRollupFromRegistry).toList();

        return PaginatedMemberRollupResponse.builder()
                .members(memberRollupView)
                .pageNumber(paginatedView.getPageNumber())
                .pageSize(paginatedView.getPageSize())
                .totalRecordCount(paginatedView.getTotalRecordCount())
                .totalPageCount(paginatedView.getTotalPageCount())
                .build();
    }

    MemberRollupView createRollupFromRegistry(MemberView memberView) {
        MemberRollupView rollupView = mapper.toDto(memberView);
        rollupView.setManagers(rollupService.getMemberManagers(memberView));
        rollupView.setAppRegistryMember(true);
        postMapperDecoration(rollupView);
        return rollupView;
    }

    // Retrieve member rollup from users in Active Directory
    PaginatedMemberRollupResponse getMemberRollupFromUser(PaginationInput paginationInput, MemberSearchFilter filter) {
        UserSearchFilter userSearchFilter = buildUserSearchFilter(paginationInput, filter);
        PaginatedMemberView paginatedView = memberService.getMembers(paginationInput, filter);
        Map<String, Long> externalIdToRegistryIdMap = paginatedView.getMembers().stream().collect(Collectors.toMap(MemberView::getExternalId, MemberView::getId));

        List<MemberRollupView> memberRollupView = getMembersFromUserSearch(userSearchFilter).parallelStream()
                .map(memberView -> createRollupFromUser(memberView, externalIdToRegistryIdMap)).toList();

        return PaginatedMemberRollupResponse.builder()
                .members(memberRollupView)
                .pageNumber(PAGE_NUMBER_AD_LIMIT)
                .pageSize(paginationInput.getPageSize())
                .totalPageCount(PAGE_NUMBER_AD_LIMIT)
                .totalRecordCount((long) memberRollupView.size())
                .build();
    }

    MemberRollupView createRollupFromUser(MemberView memberView, Map<String, Long> externalIdToRegistryIdMap) {
        MemberRollupView rollupView = mapper.toDto(memberView);
        rollupView.setManagers(rollupService.getMemberManagers(memberView));
        Long registryId = externalIdToRegistryIdMap.get(memberView.getExternalId());
        if (registryId != null) {
            rollupView.setId(registryId);
            rollupView.setAppRegistryMember(true);
        }
        postMapperDecoration(rollupView);
        return rollupView;
    }

    List<MemberView> getMembersFromUserSearch(UserSearchFilter userSearchFilter) {
        final List<User> users = switch (userSearchFilter.getSearchType()) {
            case EXTERNAL_ID -> azureAdUserService.batchFindUsers(userSearchFilter.getSearchValues().parallelStream()
                    .map(azureAdUserService::getByUserIdRequestInformation).toList());
            case EXTERNAL_ALIAS ->
                    azureAdUserService.batchFindUserCollections(userSearchFilter.getSearchValues().parallelStream()
                            .map(azureAdUserService::getByUserAliasRequestInformation).toList());
            case EMPLOYEE_ID -> azureAdUserService.batchFindUserCollections(userSearchFilter.getSearchValues().parallelStream()
                    .map(azureAdUserService::getByUserEmployeeIdRequestInformation).toList());
            default -> azureAdUserService.batchFindUserCollections(userSearchFilter.getSearchValues().parallelStream()
                    .map(azureAdUserService::getByUserNameOrEmailRequestInformation).toList());
        };
        return users.parallelStream().map(userMapper::toMemberViewDto).toList();
    }

    UserSearchFilter buildUserSearchFilter(PaginationInput paginationInput, MemberSearchFilter filter) {
        if (filter == null) {
            throw new BadRequestException(FILTER_REQUIRED);
        }
        // Validate pagination request as no pagination is used for non Registry user lookups
        validatePageNumber(paginationInput.getPageNumber());
        validatePageSize(paginationInput.getPageSize(), azureAdUserService.getSearchLimit());

        if (!StringUtils.isBlank(filter.getExternalId()) || !CollectionUtils.isEmpty(filter.getExternalIds())) {
            return buildUserSearchFilter(UserSearchType.EXTERNAL_ID, filter.getExternalId(), filter.getExternalIds(), azureAdUserService.getBatchSearchLimit());
        } else if (!StringUtils.isBlank(filter.getExternalAlias()) || !CollectionUtils.isEmpty(filter.getExternalAliases())) {
            return buildUserSearchFilter(UserSearchType.EXTERNAL_ALIAS, filter.getExternalAlias(), filter.getExternalAliases(), azureAdUserService.getBatchSearchLimit());
        } else if (!StringUtils.isBlank(filter.getEmployeeId()) || !CollectionUtils.isEmpty(filter.getEmployeeIds())) {
            return buildUserSearchFilter(UserSearchType.EMPLOYEE_ID, filter.getEmployeeId(), filter.getEmployeeIds(), azureAdUserService.getBatchSearchLimit());
        } else if (!StringUtils.isBlank(filter.getEmail()) || !CollectionUtils.isEmpty(filter.getEmails())) {
            return buildUserSearchFilter(UserSearchType.NAME_OR_EMAIL, filter.getEmail(), filter.getEmails(), azureAdUserService.getBatchSearchLimit());
        } else if (!StringUtils.isBlank(filter.getLastName())) {
            throw new BadRequestException(ExceptionReason.FILTER_TYPE_NOT_SUPPORTED,
                    String.format(ExceptionReason.FILTER_TYPE_NOT_SUPPORTED.label, "lastName"));
        } else if (!CollectionUtils.isEmpty(filter.getLastNames())) {
            throw new BadRequestException(ExceptionReason.FILTER_TYPE_NOT_SUPPORTED,
                    String.format(ExceptionReason.FILTER_TYPE_NOT_SUPPORTED.label, "lastNames"));
        } else if (!StringUtils.isBlank(filter.getName()) || !CollectionUtils.isEmpty(filter.getNames())) {
            return buildUserSearchFilter(UserSearchType.NAME_OR_EMAIL, filter.getName(), filter.getNames(), PAGE_SIZE_COLLECTION_SEARCH_LIMIT);
        } else {
            throw new BadRequestException(FILTER_REQUIRED);
        }
    }

    UserSearchFilter buildUserSearchFilter(UserSearchType searchType, String singleSearchValue, List<String> multiSearchValues, int searchLimit) {
        List<String> filterValues = combineFilterValues(singleSearchValue, multiSearchValues);
        if (filterValues.size() > searchLimit) {
            throw new BadRequestException(FILTER_REQUEST_NON_APPREG_LIMIT,
                    String.format(FILTER_REQUEST_NON_APPREG_LIMIT.label, searchLimit));
        }
        return UserSearchFilter.builder().searchType(searchType).searchValues(filterValues).build();
    }

    private void validatePageSize(int pageSize, int limit) {
        if (pageSize > limit) {
            throw new BadRequestException(ExceptionReason.FILTER_PAGE_SIZE_NON_APPREG_LIMIT,
                    String.format(ExceptionReason.FILTER_PAGE_SIZE_NON_APPREG_LIMIT.label, limit));
        }
    }

    private void validatePageNumber(int pageNumber) {
        if (pageNumber > 1) {
            throw new BadRequestException(ExceptionReason.FILTER_PAGE_NON_APPREG_LIMIT);
        }
    }

    static List<String> combineFilterValues(String value, List<String> values) {
        List<String> valueList = new ArrayList<>();
        if (StringUtils.isNotBlank(value)) {
            valueList.add(value);
        }
        if (!CollectionUtils.isEmpty(values)) {
            values.forEach(v -> {
                if (StringUtils.isNotBlank(v)) {
                    valueList.add(v);
                }
            });
        }
        return valueList;
    }
}
