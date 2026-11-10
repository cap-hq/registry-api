// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.CloudTenant;
import com.cvshealth.cap.registry.model.CloudTenantView;
import com.cvshealth.cap.registry.model.TeamAvailableCloudsAwareTeamView;
import com.cvshealth.cap.registry.service.CloudTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TeamCloudsMapperDecorator implements MapperDecoratorProvider {

    private final CloudTenantService cloudTenantService;
    private final CloudTenantViewMapper cloudTenantViewMapper;

    @Value("${app.team.available-clouds.enabled:false}")
    private boolean availableCloudsEnabled = false;

    @Override
    public void map(Object mappedView) {
        if (!availableCloudsEnabled) {
            return;
        }
        if (mappedView instanceof TeamAvailableCloudsAwareTeamView teamView) {
            try {
                List<CloudTenant> clouds = cloudTenantService.getCloudTenantsForTeamName(teamView);
                if (!CollectionUtils.isEmpty(clouds)) {
                    teamView.setAvailableClouds(cloudTenantViewMapper.toView(clouds));
                }
            } catch (Exception e) {
                log.error("Unable to retrieve available clouds for team: {}", teamView.getName(), e);
            }
        }
    }

    @Override
    public boolean supports(Class<?> mappedView) {
        return TeamAvailableCloudsAwareTeamView.class.isAssignableFrom(mappedView);
    }
}
