// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.slack.api.methods.SlackApiException;

import java.io.IOException;

@FunctionalInterface
public interface SlackApiCheckedFunction<SlackApiRequest, SlackApiTextResponse> {
    SlackApiTextResponse apply(SlackApiRequest request) throws SlackApiException, IOException;
}
