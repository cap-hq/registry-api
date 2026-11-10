// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.persistent.TeamViewEntity;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;

public class TeamViewEntityUtils {

    public static void sortChildren(TeamViewEntity entity) {
        sortApplications(entity);
        sortMembers(entity);
        sortAuthClients(entity);
    }

    static void sortApplications(TeamViewEntity entity) {
        if (CollectionUtils.isNotEmpty(entity.getApplications())) {
            entity.getApplications().sort((a1, a2) -> a1.getName().compareToIgnoreCase(a2.getName()));
        }
    }

    static void sortMembers(TeamViewEntity entity) {
        if (CollectionUtils.isNotEmpty(entity.getTeamMembers())) {
            entity.getTeamMembers().sort((m1, m2) -> m1.getId().getMember().getName().compareToIgnoreCase(m2.getId().getMember().getName()));
        }
    }

    static void sortAuthClients(TeamViewEntity entity) {
        if (CollectionUtils.isNotEmpty(entity.getTeamAuthClients())) {
            entity.getTeamAuthClients().sort((c1, c2) -> {
                int result = c1.getId().getAuthClient().getClientId().compareToIgnoreCase(c2.getId().getAuthClient().getClientId());
                if (result == 0) {
                    result = c1.getId().getAuthClient().getClientVersion().compareTo(c2.getId().getAuthClient().getClientVersion());
                }
                return result;
            });
        }
    }
}
