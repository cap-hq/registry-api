// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.annotation.FeatureFlag;
import com.cvshealth.cap.registry.rollup.model.LineOfBusinessAwareRollupView;
import com.cvshealth.cap.registry.rollup.model.LineOfBusinessView;
import com.cvshealth.cap.registry.rollup.model.UserView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.ListIterator;
import java.util.Map;

@Component
public class LineOfBusinessRollupService {

    private final LineOfBusinessService lineOfBusinessService;
    private final String orgManagerExternalid;
    private final LineOfBusinessView notInOrganizationLOB;
    private final LineOfBusinessView unknownInOrganizationLOB;

    @FeatureFlag("app.line-business.enabled")
    boolean lineOfBusinessEnabled;

    static final LineOfBusinessView UNKNOWN_LOB = LineOfBusinessView.builder().name("UNKNOWN").build();

    public LineOfBusinessRollupService(LineOfBusinessService lineOfBusinessService,
                                       @Value("${app.line-business.organization.name:ORG}") String organizationName,
                                       @Value("${app.line-business.organization.manager-external-id:}") String orgManagerExternalid) {
        this.lineOfBusinessService = lineOfBusinessService;
        this.orgManagerExternalid = orgManagerExternalid;
        this.notInOrganizationLOB = LineOfBusinessView.builder().name("NON_" + organizationName).build();
        this.unknownInOrganizationLOB = LineOfBusinessView.builder().name("UNKNOWN_IN_" + organizationName).build();
    }

    public void setLineOfBusiness(LineOfBusinessAwareRollupView lineOfBusinessRollup, boolean completeRollup) {
        if (!lineOfBusinessEnabled) {
            return;
        }
        boolean rollupInOrganization = false;
        LineOfBusinessView lineOfBusiness = null;
        List<UserView> rollupManagers = lineOfBusinessRollup.getManagers();
        if (!CollectionUtils.isEmpty(rollupManagers)) {
            // start with the highest ranking manager to find LOB manager match
            Map<String, LineOfBusinessView> managerToLobMap = lineOfBusinessService.managerToLobMap();
            ListIterator<UserView> iterator = rollupManagers.listIterator(rollupManagers.size());
            while (iterator.hasPrevious()) {
                UserView manager = iterator.previous();
                if (manager == null || manager.getExternalId() == null) {
                    continue;
                }
                if (managerToLobMap.containsKey(manager.getExternalId())) {
                    lineOfBusiness = managerToLobMap.get(manager.getExternalId());
                    break;
                }
                // check if rollup is part of organization
                if (!rollupInOrganization && manager.getExternalId().equals(orgManagerExternalid)) {
                    rollupInOrganization = true;
                }
            }
            if (lineOfBusiness == null) {
                if (rollupInOrganization) {
                    lineOfBusiness = unknownInOrganizationLOB;
                } else if (completeRollup) {
                    lineOfBusiness = notInOrganizationLOB;
                }
            }
        }
        lineOfBusinessRollup.setLineOfBusiness(lineOfBusiness == null ? UNKNOWN_LOB : lineOfBusiness);
    }
}
