// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.graphql;

import com.cvshealth.cap.registry.rollup.model.PaginatedMemberRollupResponse;
import com.cvshealth.cap.registry.rollup.model.PaginationInput;
import com.cvshealth.cap.registry.rollup.model.MemberSearchFilter;
import com.cvshealth.cap.registry.rollup.service.MemberRollupService;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@GraphQLApi
@AllArgsConstructor
public class MemberResolver {
    private final MemberRollupService memberRollupService;

    @GraphQLQuery(name = "getMemberRollup", description = "fetches member(s) with manager rollup")
    public PaginatedMemberRollupResponse getMembersWithManager(@GraphQLNonNull @Valid PaginationInput paginationInput,
                                                               @Valid MemberSearchFilter filter) {
        return memberRollupService.getMemberRollup(paginationInput, filter);
    }

}