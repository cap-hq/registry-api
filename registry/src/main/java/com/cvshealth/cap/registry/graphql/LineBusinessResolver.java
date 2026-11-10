// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.service.LineBusinessService;

import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@GraphQLApi
@AllArgsConstructor
@ConditionalOnProperty("app.admin.line-business.enabled")
public class LineBusinessResolver {
    private final LineBusinessService lineBusinessService;

    @GraphQLQuery(name = "getLinesOfBusiness", description = "fetches all lines of business")
    public List<LineBusinessView> getLinesOfBusiness() {
        return lineBusinessService.getLinesOfBusiness();
    }

    @GraphQLQuery(name = "getLineOfBusinessById", description = "fetches a line of business by id")
    public LineBusinessView getLineOfBusinessById(@GraphQLNonNull Long id) {
        return lineBusinessService.getLineBusiness(id);
    }

    @GraphQLQuery(name = "getLineOfBusinessByName", description = "fetches a line of business by name")
    public LineBusinessView getLineOfBusinessByName(@GraphQLNonNull String name) {
        return lineBusinessService.getLineBusinessByName(name);
    }

    @GraphQLMutation(name = "createLineOfBusiness", description = "creates a line of business (Registry Admin permission required)")
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public LineBusinessView createLineOfBusiness(@GraphQLNonNull LineBusinessRequest lineOfBusiness) {
        List<LineBusinessManagerView> managers = lineOfBusiness.getManagerExternalIds().stream()
                .map(id -> LineBusinessManagerView.builder().managerExternalId(id).build())
                .toList();
        return lineBusinessService.createLineOfBusiness(LineBusinessView.builder()
                .name(lineOfBusiness.getName())
                .description(lineOfBusiness.getDescription())
                .managers(managers)
                .build());
    }

    @GraphQLMutation(name = "updateLineOfBusiness", description = "updates an existing line of business (Registry Admin permission required)")
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public LineBusinessView updateLineOfBusiness(@GraphQLNonNull LineBusinessUpdateRequest lineOfBusiness) {
        List<LineBusinessManagerView> managers = lineOfBusiness.getManagerExternalIds().stream()
                .map(id -> LineBusinessManagerView.builder().managerExternalId(id).build())
                .toList();
        return lineBusinessService.updateLineOfBusiness(LineBusinessView.builder()
                .id(lineOfBusiness.getId())
                .name(lineOfBusiness.getName())
                .description(lineOfBusiness.getDescription())
                .managers(managers)
                .build());
    }

    @GraphQLMutation(name = "deleteLineOfBusiness", description = "deletes line of business by name (Registry Admin permission required)")
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public void deleteLineOfBusiness(@GraphQLNonNull String name) {
        lineBusinessService.deleteLineOfBusinessByName(name);
    }
}
