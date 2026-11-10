// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.mapper;

import com.cvshealth.cap.registry.rollup.model.LineOfBusinessAwareRollupView;
import com.cvshealth.cap.registry.rollup.model.UserView;
import com.cvshealth.cap.registry.rollup.service.LineOfBusinessRollupService;
import com.cvshealth.cap.registry.rollup.service.ManagerTitleUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ManagerRollupMapperDecorator implements MapperDecoratorProvider {

    private final Pattern managerTitleLimitPattern;
    private final LineOfBusinessRollupService lineOfBusinessRollupService;

    public ManagerRollupMapperDecorator(List<String> managerTitleLimit, LineOfBusinessRollupService lineOfBusinessRollupService) {
        this.managerTitleLimitPattern = ManagerTitleUtil.createJobTitleLevelPattern(managerTitleLimit);
        this.lineOfBusinessRollupService = lineOfBusinessRollupService;
    }

    @Override
    public void map(Object mappedView) {
        if (mappedView instanceof LineOfBusinessAwareRollupView rollupView) {
            List<UserView> managers = rollupView.getManagers();
            boolean completeRollup = false;
            List<UserView> trimmedManagers = null;

            if (managers != null && managerTitleLimitPattern != null) {
                trimmedManagers = new ArrayList<>();
                for (UserView manager : managers) {
                    trimmedManagers.add(manager);
                    String title = manager == null || manager.getTitle() == null ? "" : manager.getTitle();
                    if (managerTitleLimitPattern.matcher(title).matches()) {
                        completeRollup = true;
                        break;
                    }
                }
            }

            // (1) set LOB based on full manager hierarchy w/info on if manager title limit exists
            lineOfBusinessRollupService.setLineOfBusiness(rollupView, completeRollup);

            // (2) trim manager list to title limit
            if (trimmedManagers != null) {
                rollupView.setManagers(trimmedManagers);
            }
        }
    }

    @Override
    public boolean supports(Class<?> mappedView) {
        return LineOfBusinessAwareRollupView.class.isAssignableFrom(mappedView);
    }
}
