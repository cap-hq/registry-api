// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.User;
import com.cvshealth.cap.registry.service.AzureAdUserService;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@GraphQLApi
@AllArgsConstructor
public class UserResolver {
    private final AzureAdUserService azureAdUserService;

    @GraphQLQuery(name = "getExternalUsers", description = "fetches external users by name or email wildcard")
    public List<User> getExternalUsers(@GraphQLNonNull String nameOrEmail, Boolean includedDisabledAccounts) {
        List<User> allByNameOrEmail = azureAdUserService.findAllByNameOrEmail(nameOrEmail, includedDisabledAccounts);
        return allByNameOrEmail.stream().filter(user -> user.getEmployeeId() != null).toList();
    }

    @GraphQLQuery(name = "getExternalUserWithManager", description = "fetches external users by external Id")
    public User getExternalUserWithManager(@GraphQLNonNull String externalId) {
        Optional<User> result = azureAdUserService.findByIdWithManager(externalId);
        return result.orElse(null);
    }
}
