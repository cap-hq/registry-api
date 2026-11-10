// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class AzureAdUserServiceValidator {

    protected static final int SEARCH_MIN_LENGTH = 3;

    public String validate(String request) throws ApiException {
        if (StringUtils.isEmpty(request)) {
            throw new BadRequestException(ExceptionReason.USER_SEARCH_REQUIRED);
        }
        if (request.length() < SEARCH_MIN_LENGTH) {
            throw new BadRequestException(ExceptionReason.USER_SEARCH_INVALID_LENGTH,
                    String.format(ExceptionReason.USER_SEARCH_INVALID_LENGTH.label, SEARCH_MIN_LENGTH));

        }
        return request;
    }

}
