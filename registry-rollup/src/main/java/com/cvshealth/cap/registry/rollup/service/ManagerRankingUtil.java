// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.config.TitleRankingsConfig;
import com.cvshealth.cap.registry.rollup.model.User;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ManagerRankingUtil {

    @Autowired
    private TitleRankingsConfig titleRankingsConfig;


    /**
     * Obtains the lowest ranked manager external Id based on the Job title
     * @param managerRanks List of manager scores
     * @return String of manager ecternal Id
     */
    public String getLowestRankedManagerExternalId(List<Pair<User, Long>> managerRanks) {
        Long highestRank = 1L;

        for (Pair<User, Long> managerRank : managerRanks) {
            if (managerRank.getValue() > highestRank) {
                highestRank = managerRank.getValue();
            }
        }

        List<Pair<User, Long>> tiedManagerRanks;

        final Long highestManagerRank = highestRank;

        tiedManagerRanks = managerRanks.stream().filter(managerRank -> managerRank.getValue().equals(highestManagerRank))
                .toList();

        List<Pair<User, Integer>> mapManagerToWeight = new ArrayList<Pair<User, Integer>>();

        for (Pair<User, Long> managerRank : tiedManagerRanks) {
            User manager = managerRank.getKey();
            // the prefix will remove all spaces and return srmgr instead of SR MGR
            String titlePrefix = this.getTitlePrefix(manager.getTitle()).replaceAll("\\s+", "");

            Integer titleWeight = titleRankingsConfig.getRankings().get(titlePrefix);
            mapManagerToWeight.add(new MutablePair<User, Integer>(manager, Objects.requireNonNullElse(titleWeight, 0)));


        }

        mapManagerToWeight.sort(Comparator.comparing(((Pair<User, Integer> p) -> p.getValue()))
                .thenComparing(p -> p.getKey().getExternalAlias()));

        return mapManagerToWeight.get(0).getKey().getExternalId();
    }

    /**
     * The job title prefix in a job title
     * @param title Job title (Ex: Mgr, Software)
     * @return String the jobtitle prefix Ex: mgr
     */
    public String getTitlePrefix(String title) {
        if (title == null) return "";

        List<String> titlesList = this.titleRankingsConfig.getAllTitleKeys()
                .stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .collect(Collectors.toList());

        // Create a version of the title without spaces for matching
        String titleWithoutSpaces = title.toLowerCase().replaceAll("\\s+", "");

        for (String titleKey : titlesList) {
            String lowerTitleKey = titleKey.toLowerCase();

            // Check if the title (without spaces) contains the title key
            if (titleWithoutSpaces.contains(lowerTitleKey)) {
                return lowerTitleKey;
            }
        }

        return "";
    }

}