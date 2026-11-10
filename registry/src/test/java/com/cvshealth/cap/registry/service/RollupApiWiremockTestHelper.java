// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.rollup.LineOfBusinessView;
import com.cvshealth.cap.registry.model.rollup.TeamRollupResult;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import com.cvshealth.cap.registry.model.rollup.UserView;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import wiremock.com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class RollupApiWiremockTestHelper {

    private final WireMockServer wiremockServer;

    public RollupApiWiremockTestHelper(int portNumber) {
        wiremockServer = new WireMockServer(options().port(portNumber));
    }

    public void start() {
        wiremockServer.start();
    }

    public void stop() {
        wiremockServer.stop();
    }

    public void registerRollupApiMock(String teamName) {
        registerRollupApiMock(teamName, false);
    }

    public void registerRollupApiMock(String teamName, boolean includeLOB) {
        TeamRollupView.TeamRollupViewBuilder builder = TeamRollupView.builder().name(teamName)
                .managers(List.of(
                        UserView.builder().title("Sr.Mgr,Software").lastName("Manager").firstName("Senior").build(),
                        UserView.builder().title("VP,Software").lastName("President").firstName("Vice").build(),
                        UserView.builder().title("SVP,Software").lastName("President").firstName("Senior Vice").build()
                ));
        if (includeLOB) {
            builder.lineOfBusiness(LineOfBusinessView.builder().name("LOB1").build());
        }
        TeamRollupView teamRollupView = builder.build();
        wiremockServer.stubFor(post("/graphql")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mockTeamRollupResult(teamRollupView))));
    }

    private JsonNode mockTeamRollupResult(TeamRollupView teamRollupView) {
        String json = """
                {
                	"data": {
                		"getTeamRollup": %s
                	}
                }
                """;
        try {
            TeamRollupResult teamRollupResult = TeamRollupResult.builder()
                    .teams(List.of(teamRollupView))
                    .build();
            wiremock.com.fasterxml.jackson.databind.ObjectMapper mapper = new wiremock.com.fasterxml.jackson.databind.ObjectMapper();
            json = String.format(json, mapper.writeValueAsString(teamRollupResult));
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void verifyIfContainsLineOfBusiness(boolean includeLOB) {
        StringValuePattern pattern = includeLOB ? matching(".*lineOfBusiness.*") : notMatching(".*lineOfBusiness.*");
        wiremockServer.verify(1,
                RequestPatternBuilder.newRequestPattern()
                        .withUrl("/graphql")
                        .withRequestBody(pattern));
    }
}
