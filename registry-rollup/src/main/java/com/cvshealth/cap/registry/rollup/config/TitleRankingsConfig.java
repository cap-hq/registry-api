// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "app.rollup.title-rankings")
@Component
@Data
@Slf4j
public class TitleRankingsConfig {

    private Map<String, Integer> rankings = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("TitleRankingsConfig loaded with rankings: {}", rankings);
    }

    public Integer getRanking(String titlePrefix) {
        return rankings.getOrDefault(titlePrefix, Integer.MAX_VALUE);
    }

    public List<String> getAllTitleKeys() {
        return rankings.keySet().stream()
                .collect(Collectors.toList());
    }
}