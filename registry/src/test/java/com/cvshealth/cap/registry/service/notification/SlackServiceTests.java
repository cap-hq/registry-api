// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.notification;

import com.cvshealth.cap.registry.config.SlackClientConfig;
import com.cvshealth.cap.registry.model.exception.SlackServiceException;
import com.slack.api.methods.AsyncMethodsClient;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiErrorResponse;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.conversations.ConversationsInfoRequest;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.conversations.ConversationsInfoResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.ResponseMetadata;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SlackServiceTests {

    @InjectMocks
    private SlackService slackService;

    private SlackService slackServiceSpy;

    @Mock
    private SlackClientConfig slackClientConfig;

    @Mock
    private MethodsClient methodsClient;

    @Mock
    private AsyncMethodsClient asyncMethodsClient;

    @Mock
    private ChatPostMessageResponse chatPostMessageResponse;

    @Mock
    private ConversationsListResponse conversationsListResponse;

    @Mock
    private ConversationsInfoResponse conversationsInfoResponse;

    @Mock
    private Conversation conversation;

    @Mock
    private ResponseMetadata responseMetadata;

    @Mock
    private okhttp3.Response httpResponse;

    @Mock
    private SlackApiErrorResponse slackApiErrorResponse;

    @Mock
    private SlackApiException slackApiException;

    @Mock
    private IOException ioException;

    @BeforeEach
    public void setupConfig() {
        slackServiceSpy = spy(slackService);
    }

    @Test
    public void testSendMessage_okay() throws SlackServiceException, SlackApiException, IOException {
        // given
        whenSlackEnabledForTest();
        when(chatPostMessageResponse.isOk()).thenReturn(true);
        when(methodsClient.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(chatPostMessageResponse);

        // when
        slackServiceSpy.sendMessageToChannel("CH123", "test", "test");
    }

    @Test
    public void testSendMessage_NotOkay() throws SlackApiException, IOException {
        // given
        whenSlackEnabledForTest();
        when(chatPostMessageResponse.isOk()).thenReturn(false);
        when(chatPostMessageResponse.getError()).thenReturn("channel_not_found");
        when(methodsClient.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(chatPostMessageResponse);

        // when
        SlackServiceException ex = assertThrows(SlackServiceException.class, () -> slackServiceSpy.sendMessageToChannel("CH123", "test", "test"));
        Assertions.assertThat(ex.getMessage()).isEqualTo("channel_not_found");
    }

    @Test
    public void testSendMessage_postMessage_NotOkay() throws SlackApiException, IOException {
        // given
        whenSlackEnabledForTest();
        when(methodsClient.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(chatPostMessageResponse);
        when(chatPostMessageResponse.isOk()).thenReturn(false);
        when(chatPostMessageResponse.getError()).thenReturn("some_error");

        // when
        SlackServiceException ex = assertThrows(SlackServiceException.class, () -> slackServiceSpy.sendMessageToChannel("CH123", "test", "test"));
        Assertions.assertThat(ex.getMessage()).isEqualTo("some_error");
    }

    @Test
    public void testSendMessage_postMessage_throwsSlackApiException() throws SlackApiException, IOException {
        // given
        whenSlackEnabledForTest();
        when(httpResponse.code()).thenReturn(500);
        when(slackApiException.getResponse()).thenReturn(httpResponse);
        when(slackApiErrorResponse.getError()).thenReturn("some_error");
        when(slackApiException.getError()).thenReturn(slackApiErrorResponse);
        when(methodsClient.chatPostMessage(any(ChatPostMessageRequest.class))).thenThrow(slackApiException);

        // then
        SlackServiceException ex = assertThrows(SlackServiceException.class, () -> slackServiceSpy.sendMessageToChannel("CH123", "test", "test"));
        Assertions.assertThat(ex.getMessage()).isEqualTo("some_error");
    }

    @Test
    public void testSendMessage_throwsIOException() throws SlackApiException, IOException {
        // given
        whenSlackEnabledForTest();
        when(slackClientConfig.isEnabled()).thenReturn(true);
        when(ioException.getMessage()).thenReturn("some_io_error");
        when(methodsClient.chatPostMessage(any(ChatPostMessageRequest.class))).thenThrow(ioException);

        // then
        SlackServiceException ex = assertThrows(SlackServiceException.class, () -> slackServiceSpy.sendMessageToChannel("CH123", "test", "test"));
        Assertions.assertThat(ex.getMessage()).isEqualTo("some_io_error");
    }

    @Test
    public void testSendAsyncMessage() throws ExecutionException, InterruptedException {
        // given
        whenSlackEnabledForAsyncTest();
        when(chatPostMessageResponse.isOk()).thenReturn(true);
        when(asyncMethodsClient.chatPostMessage(any(ChatPostMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(chatPostMessageResponse));

        // when
        CompletableFuture<ChatPostMessageResponse> futureResult = slackServiceSpy.sendAsyncMessageToChannel("CH123", "test", "test");

        // then
        assertThat(futureResult).isNotNull();
        ChatPostMessageResponse result = futureResult.get();
        assertThat(result).isNotNull();
        assertThat(result.isOk()).isTrue();
    }

    @Test
    public void testSendAsyncMessage_retryNotOkay_thenSuccess() throws ExecutionException, InterruptedException {
        // given
        whenSlackEnabledForAsyncTest();
        when(slackClientConfig.getPostMessageRetries()).thenReturn(1);
        ChatPostMessageResponse firstTryResponse = mock(ChatPostMessageResponse.class);
        when(firstTryResponse.isOk()).thenReturn(false);
        when(firstTryResponse.getError()).thenReturn("ratelimited");
        when(firstTryResponse.getHttpResponseHeaders()).thenReturn(Map.of("Retry-After", List.of("1")));
        when(chatPostMessageResponse.isOk()).thenReturn(true);
        when(asyncMethodsClient.chatPostMessage(any(ChatPostMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(firstTryResponse))
                .thenReturn(CompletableFuture.completedFuture(chatPostMessageResponse));

        // when
        CompletableFuture<ChatPostMessageResponse> futureResult = slackServiceSpy.sendAsyncMessageToChannel("CH123", "test", "test");

        // then
        assertThat(futureResult).isNotNull();
        ChatPostMessageResponse result = futureResult.get();
        assertThat(result).isNotNull();
        assertThat(result.isOk()).isTrue();
    }

    @Test
    public void testSendAsyncMessage_retryException_thenSuccess() throws ExecutionException, InterruptedException {
        // given
        whenSlackEnabledForAsyncTest();
        when(slackClientConfig.getPostMessageRetries()).thenReturn(1);
        when(httpResponse.code()).thenReturn(429);
        when(httpResponse.header("Retry-After")).thenReturn("1");
        when(slackApiException.getResponse()).thenReturn(httpResponse);
        when(chatPostMessageResponse.isOk()).thenReturn(true);
        CompletableFuture<ChatPostMessageResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(slackApiException);
        when(asyncMethodsClient.chatPostMessage(any(ChatPostMessageRequest.class)))
                .thenReturn(failedFuture)
                .thenReturn(CompletableFuture.completedFuture(chatPostMessageResponse));

        // when
        CompletableFuture<ChatPostMessageResponse> futureResult = slackServiceSpy.sendAsyncMessageToChannel("CH123", "test", "test");

        // then
        assertThat(futureResult).isNotNull();
        ChatPostMessageResponse result = futureResult.get();
        assertThat(result).isNotNull();
        assertThat(result.isOk()).isTrue();
    }

    @Test
    public void testSendAsyncMessage_exhaustRetries() {
        // given
        whenSlackEnabledForAsyncTest();
        when(slackClientConfig.getPostMessageRetries()).thenReturn(1);
        ChatPostMessageResponse firstTryResponse = mock(ChatPostMessageResponse.class);
        when(firstTryResponse.isOk()).thenReturn(false);
        when(firstTryResponse.getError()).thenReturn("ratelimited");
        when(firstTryResponse.getHttpResponseHeaders()).thenReturn(Map.of("Retry-After", List.of("1")));
        when(chatPostMessageResponse.isOk()).thenReturn(false);
        when(chatPostMessageResponse.getError()).thenReturn("ratelimited");
        when(asyncMethodsClient.chatPostMessage(any(ChatPostMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(firstTryResponse))
                .thenReturn(CompletableFuture.completedFuture(chatPostMessageResponse));

        // when
        CompletableFuture<ChatPostMessageResponse> futureResult = slackServiceSpy.sendAsyncMessageToChannel("CH123", "test", "test");

        // then
        assertThat(futureResult).isNotNull();
        ExecutionException ex = assertThrows(ExecutionException.class, () -> futureResult.get());
        assertThat(ex.getCause().getMessage()).isEqualTo("ratelimited");
    }

    @Test
    public void testSendAsyncMessage_exhaustRetries_AfterException() throws ExecutionException, InterruptedException {
        // given
        whenSlackEnabledForAsyncTest();
        when(slackClientConfig.getPostMessageRetries()).thenReturn(1);
        when(httpResponse.code()).thenReturn(429);
        when(httpResponse.header("Retry-After")).thenReturn("1");
        when(slackApiException.getResponse()).thenReturn(httpResponse);
        CompletableFuture<ChatPostMessageResponse> failedFuture1 = new CompletableFuture<>();
        failedFuture1.completeExceptionally(slackApiException);

        CompletableFuture<ChatPostMessageResponse> failedFuture2 = new CompletableFuture<>();
        SlackApiException slackApiException2 = mock(SlackApiException.class);
        failedFuture2.completeExceptionally(slackApiException2);
        when(asyncMethodsClient.chatPostMessage(any(ChatPostMessageRequest.class)))
                .thenReturn(failedFuture1)
                .thenReturn(failedFuture2);

        // when
        CompletableFuture<ChatPostMessageResponse> futureResult = slackServiceSpy.sendAsyncMessageToChannel("CH123", "test", "test");

        // then
        assertThat(futureResult).isNotNull();
        assertThat(futureResult).isNotNull();
        assertThrows(ExecutionException.class, () -> futureResult.get());
    }

    @Test
    public void testGetChannels() throws SlackServiceException, SlackApiException, IOException {
        // given
        whenSlackEnabledForTest();
        String channelName = "channel-1";
        whenGoodListConversions();

        // when
        Map<String, Conversation> channels = slackServiceSpy.getChannels();

        // then
        assertThat(channels.containsKey(channelName)).isTrue();
        Conversation channel = channels.get(channelName);
        assertThat(channel.getId()).isEqualTo("C123");
        assertThat(channel.getName()).isEqualTo(channelName);
        assertThat(channel.getNumOfMembers()).isEqualTo(1);
    }

    @Test
    public void testGetChannels_NotOkay() throws SlackApiException, IOException {
        // given
        whenSlackEnabledForTest();
        when(conversationsListResponse.isOk()).thenReturn(false);
        when(conversationsListResponse.getError()).thenReturn("missing_scope");
        when(methodsClient.conversationsList(any(ConversationsListRequest.class))).thenReturn(conversationsListResponse);

        // when
        SlackServiceException ex = assertThrows(SlackServiceException.class, () -> slackServiceSpy.getChannels());
        Assertions.assertThat(ex.getMessage()).isEqualTo("missing_scope");
    }

    @Test
    public void testChannelInfo() throws SlackServiceException, SlackApiException, IOException {
        // given
        whenSlackEnabledForTest();
        String channelId = "CID123";

        // then
        when(conversationsInfoResponse.isOk()).thenReturn(true);
        when(conversationsInfoResponse.getChannel()).thenReturn(conversation);
        when(methodsClient.conversationsInfo(any(ConversationsInfoRequest.class))).thenReturn(conversationsInfoResponse);

        // when
        Conversation channel = slackServiceSpy.getChannelInfo(channelId);

        // then
        assertThat(channel).isNotNull();
    }

    @Test
    public void testChannelInfo_notFound() throws SlackServiceException, SlackApiException, IOException {
        // given
        whenSlackEnabledForTest();
        String channelId = "CID123";

        // then
        when(conversationsInfoResponse.isOk()).thenReturn(false);
        when(conversationsInfoResponse.getError()).thenReturn("channel_not_found");
        when(methodsClient.conversationsInfo(any(ConversationsInfoRequest.class))).thenReturn(conversationsInfoResponse);

        // when
        Conversation channel = slackServiceSpy.getChannelInfo(channelId);

        // then
        assertThat(channel).isNull();
    }

    @Test
    public void testChannelInfo_error() throws SlackServiceException, SlackApiException, IOException {
        // given
        whenSlackEnabledForTest();
        String channelId = "CID123";

        // then
        when(conversationsInfoResponse.isOk()).thenReturn(false);
        when(conversationsInfoResponse.getError()).thenReturn("some_error");
        when(methodsClient.conversationsInfo(any(ConversationsInfoRequest.class))).thenReturn(conversationsInfoResponse);

        // when
        assertThrows(SlackServiceException.class, () -> slackServiceSpy.getChannelInfo(channelId));
    }

    private void whenGoodListConversions() throws SlackApiException, IOException {
        when(conversationsListResponse.isOk()).thenReturn(true);
        when(conversationsListResponse.getChannels()).thenReturn(
                List.of(conversation));
        when(conversationsListResponse.getResponseMetadata()).thenReturn(responseMetadata);
        when(responseMetadata.getNextCursor()).thenReturn("");
        when(conversation.getId()).thenReturn("C123");
        when(conversation.getName()).thenReturn("channel-1");
        when(conversation.getNumOfMembers()).thenReturn(1);
        when(methodsClient.conversationsList(any(ConversationsListRequest.class))).thenReturn(conversationsListResponse);
    }

    private void whenSlackEnabledForTest() {
        when(slackClientConfig.isEnabled()).thenReturn(true);
        doReturn(methodsClient).when(slackServiceSpy).methodsClient();
    }

    private void whenSlackEnabledForAsyncTest() {
        when(slackClientConfig.isEnabled()).thenReturn(true);
        doReturn(asyncMethodsClient).when(slackServiceSpy).asyncMethodsClient();
    }
}
