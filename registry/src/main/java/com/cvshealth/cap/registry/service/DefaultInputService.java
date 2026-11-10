// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.SlackClientConfig;
import com.cvshealth.cap.registry.model.DataPerimeter;
import com.cvshealth.cap.registry.model.InformationClassification;
import com.cvshealth.cap.registry.model.ValidationConstraints;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultInputService implements InputService {

    private final ComplianceTypeService complianceTypeService;
    private final PriorityService priorityService;
    private final CloudTenantService cloudTenantService;
    private final SlackClientConfig slackClientConfig;
    private final ValidationConstraints validationConstraints;

    // the initializer keeps today's behavior wherever Spring does not inject the property
    @Value("${app.legacy-compliance-types.enabled:true}")
    private boolean legacyComplianceTypesEnabled = true;

    public Map<String, Object> getInputs() {
        Map<String, Object> inputs = new HashMap<>();

        // the options stay as they are in both modes: the UI requires at least one, and uses them to label existing tags
        inputs.put("complianceTypes", complianceTypeService.getComplianceTypes());
        // lets the UI choose its form without a matching setting of its own
        inputs.put("legacyComplianceTypesEnabled", legacyComplianceTypesEnabled);
        // PUBLIC is left out until the cutover: no compliance tag maps to it, so the must-match check would reject it
        inputs.put("informationClassifications", Arrays.stream(InformationClassification.values())
                .filter(value -> !legacyComplianceTypesEnabled || value != InformationClassification.PUBLIC)
                .map(value -> Map.of("name", value.name(), "label", value.getLabel()))
                .toList());
        inputs.put("dataPerimeters", Arrays.stream(DataPerimeter.values())
                .map(value -> Map.of("name", value.name(), "label", value.getLabel()))
                .toList());
        inputs.put("applicationPriorities", priorityService.getPriority());
        inputs.put("clouds", cloudTenantService.getCloudTenants());
        inputs.put("slackBotApp", slackClientConfig.getAppName());
        // Published as one entity-keyed tree so the wire shape follows the model — see
        // ValidationConstraints#published for why this is a copy.
        inputs.put("validation", validationConstraints.published());

        return inputs;
    }
}
