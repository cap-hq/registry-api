// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.mapper.ApplicationMapper;
import com.cvshealth.cap.registry.rollup.mapper.MapperDecoratorProvider;
import com.cvshealth.cap.registry.rollup.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ApplicationRollupService extends AbstractRollupService {
    private final ApplicationService applicationService;
    private final ApplicationMapper applicationMapper;
    private final RollupService rollupService;

    public ApplicationRollupService(ApplicationService applicationService,
                                    ApplicationMapper applicationMapper,
                                    RollupService rollupService,
                                    List<MapperDecoratorProvider> mapperDecoratorProviders) {
        super(mapperDecoratorProviders);
        this.applicationService = applicationService;
        this.applicationMapper = applicationMapper;
        this.rollupService = rollupService;
    }

    public PaginatedApplicationRollupResponse getApplicationRollup(PaginationInput paginationInput, ApplicationSearchFilter filter) {
        log.debug("applicationRollup for filter[{}]", filter);
        // get applications from registry
        PaginatedApplicationView paginatedApplicationView = applicationService.getApplications(paginationInput, filter);
        List<ApplicationRollupView> applicationRollupViews = paginatedApplicationView.getApplications().parallelStream()
                .map(this::createRollup).toList();

        // return application data with manager rollup
        return PaginatedApplicationRollupResponse.builder()
                .applications(applicationRollupViews)
                .pageNumber(paginatedApplicationView.getPageNumber())
                .pageSize(paginatedApplicationView.getPageSize())
                .totalRecordCount(paginatedApplicationView.getTotalRecordCount())
                .totalPageCount(paginatedApplicationView.getTotalPageCount())
                .build();
    }

    ApplicationRollupView createRollup(ApplicationView applicationView) {
        ApplicationRollupView applicationRollupView = applicationMapper.toDto(applicationView);
        applicationRollupView.setManagers(rollupService.getTeamManagers(applicationView.getTeam()));
        postMapperDecoration(applicationRollupView);
        return applicationRollupView;
    }
}
