// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.TeamRollupAwareTeamView;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import com.cvshealth.cap.registry.service.RegistryRollupService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientException;

@Component
@AllArgsConstructor
@Slf4j
public class TeamRollupMapperDecorator implements MapperDecoratorProvider {

    private final RegistryRollupService registryRollupService;

    @Override
    public void map(Object mappedView) {
        if (mappedView instanceof TeamRollupAwareTeamView teamView) {
            try {
                TeamRollupView teamRollup = registryRollupService.getTeamRollup(teamView.getName());
                if (teamRollup != null) {
                    teamView.setTeamRollup(teamRollup);
                }
            } catch (WebClientException e) {
                log.error("Unable to retrieve team rollup info for team: {}", teamView.getName(), e);
            }
        }
    }

    @Override
    public boolean supports(Class<?> mappedView) {
        return TeamRollupAwareTeamView.class.isAssignableFrom(mappedView);
    }
}
