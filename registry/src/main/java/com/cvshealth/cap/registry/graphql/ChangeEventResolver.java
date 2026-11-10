// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.PaginatedChangeEventViewResponse;
import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.model.audit.ChangeEventGetRequest;
import com.cvshealth.cap.registry.model.audit.ChangeEventUpdateRequest;
import com.cvshealth.cap.registry.model.audit.ChangeEventView;
import com.cvshealth.cap.registry.service.audit.ChangeEventService;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
@GraphQLApi
@AllArgsConstructor
public class ChangeEventResolver {

    private final ChangeEventService changeEventService;

    @GraphQLQuery(name = "getChangeEvents",
            description = "fetches all change event records for a given client id")
    public PaginatedChangeEventViewResponse getChangeEvents(@GraphQLNonNull @Valid PaginationInput paginationInput,
                                                            @GraphQLNonNull ChangeEventGetRequest request) {
        return changeEventService.getChangeEvents(paginationInput, request);
    }

    @GraphQLMutation(name = "syncChangeEvent", description = "updates the change event sync status for a given client Id")
    @PreAuthorize("hasPermission(#request.clientId, 'ChangeEvent', 'sync-change-event')")
    public ChangeEventView syncChangeEvent(@GraphQLNonNull ChangeEventUpdateRequest request) {
        return changeEventService.syncChangeEvent(request);
    }
}
