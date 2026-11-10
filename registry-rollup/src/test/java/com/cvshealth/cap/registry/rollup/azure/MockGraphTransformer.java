// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.azure;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

/**
 * Answers every request to the mock Graph server by delegating to {@link MockGraphRouter}.
 */
final class MockGraphTransformer implements ResponseDefinitionTransformerV2 {

    @Override
    public ResponseDefinition transform(ServeEvent serveEvent) {
        LoggedRequest request = serveEvent.getRequest();
        MockGraphRouter.MockResponse response = MockGraphRouter.handle(
                request.getMethod().getName(), request.getUrl(), request.getBodyAsString());
        return ResponseDefinitionBuilder.responseDefinition()
                .withStatus(response.status())
                .withHeader("Content-Type", "application/json")
                .withBody(response.body().toString())
                .build();
    }

    @Override
    public boolean applyGlobally() {
        return true;
    }

    @Override
    public String getName() {
        return "mock-graph";
    }
}
