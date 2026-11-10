// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.notification;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.model.exception.SlackServiceException;
import com.github.tomakehurst.wiremock.WireMockServer;

import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.Conversation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import wiremock.com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.cvshealth.cap.registry.util.WiremockUtils.getJsonNode;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

public class SlackServiceIntegrationTests extends RegistryIntegrationTests {

    private static final String PRIVATE_CHANNEL_1_ID = "CID-1";
    public static final String  PRIVATE_CHANNEL_1_NAME = "private-channel-1";
    public static final String  PRIVATE_CHANNEL_2_NAME = "private-channel-2";

    @Autowired
    private SlackService slackService;

    private WireMockServer wiremockServer;

    @BeforeEach
    public void start() {
        wiremockServer = new WireMockServer(options().port(9999));
        wiremockServer.start();
    }

    @AfterEach
    public void stop() {
        wiremockServer.stop();
    }

    @Test
    public void testSendMessage() throws SlackServiceException {
        // given
        stubSlackApiCall("/api/auth.test", goodAuthTestResponse());
        stubSlackApiCall("/api/chat.postMessage", goodPostMessageResponse());

        // when
        slackService.sendMessageToChannel(PRIVATE_CHANNEL_1_ID, PRIVATE_CHANNEL_1_NAME, "test");

        // then
        wiremockServer.checkForUnmatchedRequests();
    }

    @Test
    public void testSendAsyncMessage() throws ExecutionException, InterruptedException {
        // given
        stubSlackApiCall("/api/auth.test", goodAuthTestResponse());
        stubSlackApiCall("/api/chat.postMessage", goodPostMessageResponse());

        // when
        CompletableFuture<ChatPostMessageResponse> futureResponse = slackService.sendAsyncMessageToChannel(PRIVATE_CHANNEL_1_ID, PRIVATE_CHANNEL_1_NAME, "test");

        // then
        ChatPostMessageResponse response = futureResponse.get();
        assertThat(response.isOk()).isTrue();
        wiremockServer.checkForUnmatchedRequests();
    }

    @Test
    public void testGetChannels() throws SlackServiceException {
        // given
        stubSlackApiCall("/api/auth.test", goodAuthTestResponse());
        stubSlackApiCall("/api/conversations.list", goodGetChannelsResponse());

        // when
        Map<String, Conversation> channels = slackService.getChannels();

        // then
        assertThat(channels.size()).isEqualTo(2);
        assertThat(channels.get(PRIVATE_CHANNEL_1_NAME)).isNotNull();
        assertThat(channels.get(PRIVATE_CHANNEL_2_NAME)).isNotNull();
        wiremockServer.checkForUnmatchedRequests();
    }

    @Test
    public void testChannelInfo() throws SlackServiceException {
        // given
        stubSlackApiCall("/api/auth.test", goodAuthTestResponse());
        stubSlackApiCall("/api/conversations.info", goodChannelInfoResponse());

        // when
        Conversation channel = slackService.getChannelInfo(PRIVATE_CHANNEL_1_ID);
        assertThat(channel).isNotNull();

        wiremockServer.checkForUnmatchedRequests();
    }

    @Test
    public void testChannelInfo_cached() throws SlackServiceException {
        // given
        stubSlackApiCall("/api/auth.test", goodAuthTestResponse());
        stubSlackApiCall("/api/conversations.info", goodChannelInfoResponse());

        // when
        Conversation channel = slackService.getChannelInfo(PRIVATE_CHANNEL_1_ID);
        assertThat(channel).isNotNull();

        // when subsequent call
        channel = slackService.getChannelInfo(PRIVATE_CHANNEL_1_ID);
        assertThat(channel).isNotNull();

        wiremockServer.checkForUnmatchedRequests();
        wiremockServer.verify(1, RequestPatternBuilder.newRequestPattern()
                .withUrl("/api/conversations.info"));
    }

    private static JsonNode goodAuthTestResponse() {
        return getJsonNode("""
                {
                	"ok": true
                }
                """);
    }

    private static JsonNode goodPostMessageResponse() {
        return getJsonNode("""
                {
                	"ok": true
                }
                """);
    }

    private static JsonNode goodGetChannelsResponse() {
        return getJsonNode("""
                {
                	"ok": true,
                	"channels": [
                        {
                            "id": "CID-1",
                            "name": "private-channel-1",
                            "is_private": true,
                            "num_members": 10
                        },
                        {
                            "id": "CID-2",
                            "name": "private-channel-2",
                            "is_private": true,
                            "num_members": 1
                        }
                	],
                    "response_metadata": {
                        "next_cursor": ""
                    }
                }
                """);
    }

    private static JsonNode goodChannelInfoResponse() {
        return getJsonNode("""
                {
                	"ok": true,
                	"channel": {
                        "id": "CID-1",
                            "name": "private-channel-1",
                            "is_private": true,
                            "num_members": 10
                    }
                }
                """);
    }

    private void stubSlackApiCall(String endpoint, JsonNode response) {
        wiremockServer.stubFor(post(endpoint)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(response)));
    }
}
