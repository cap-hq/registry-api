// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.Member;
import com.cvshealth.cap.registry.model.MemberRequest;
import com.cvshealth.cap.registry.model.MemberSearchFilter;
import com.cvshealth.cap.registry.model.MemberView;
import com.cvshealth.cap.registry.model.PaginatedMemberResponse;
import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.service.RegistryService;
import com.cvshealth.cap.registry.service.MemberService;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;


@Component
@GraphQLApi
@AllArgsConstructor
public class MemberResolver {
    private final MemberService memberService;
    private final RegistryService registryService;

    @GraphQLQuery(name = "getMembers", description = "fetches all registry members")
    public PaginatedMemberResponse getMembers(@GraphQLNonNull @Valid PaginationInput paginationInput,
                                              @Valid MemberSearchFilter filter,
                                              @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return memberService.getMembers(paginationInput, filter, graphQLEnv);
    }

    @GraphQLQuery(name = "getMember", description = "fetches registry member by id")
    public MemberView getMember(@GraphQLNonNull Long id, @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return memberService.getMemberView(id, graphQLEnv);
    }

    @GraphQLQuery(name = "getMemberByExternalId", description = "fetches registry member by external id")
    public MemberView getMemberByExternalId(@GraphQLNonNull String externalId, @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return memberService.getMemberView(registryService.getMemberByExternalId(externalId).getId(), graphQLEnv);
    }

    @GraphQLQuery(name = "getMemberByExternalAlias", description = "fetches registry member by external alias")
    public MemberView getMemberByExternalAlias(@GraphQLNonNull String externalAlias, @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return memberService.getMemberView(registryService.getMemberByExternalAlias(externalAlias).getId(), graphQLEnv);
    }

    @GraphQLQuery(name = "getMemberByEmail", description = "fetches registry member by email")
    public MemberView getMemberByEmail(@GraphQLNonNull String email, @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return memberService.getMemberView(MemberSearchFilter.builder().email(email).build(), graphQLEnv);
    }

    @GraphQLMutation(name = "createMemberByExternalAlias", description = "creates registry member by externalAlias")
    @PreAuthorize("hasPermission(null, 'Member', 'create')")
    public Member createMemberByExternalAlias(@GraphQLNonNull String externalAlias) {
        return registryService.getMemberByExternalAlias(externalAlias);
    }

    @GraphQLMutation(name = "createMemberByExternalId", description = "creates registry member by externalId")
    @PreAuthorize("hasPermission(null, 'Member', 'create')")
    public Member createMemberByExternalId(@GraphQLNonNull String externalId) {
        return registryService.getMemberByExternalId(externalId);
    }

    @GraphQLMutation(name = "updateMemberByExternalAlias", description = "updates registry member by externalAlias")
    @PreAuthorize("hasPermission(#member.externalAlias, 'Member', 'update')")
    public Member updateMemberByExternalAlias(@GraphQLNonNull MemberRequest member) {
        return registryService.updateMemberByExternalAlias(member);
    }

    @GraphQLMutation(name = "updateMemberByExternalId", description = "updates registry member by externalId")
    @PreAuthorize("hasPermission(#member.externalId, 'Member', 'update')")
    public Member updateMemberByExternalId(@GraphQLNonNull MemberRequest member) {
        return registryService.updateMemberByExternalId(member);
    }

}
