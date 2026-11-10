// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.notification;

import com.cvshealth.cap.registry.config.CacheConfig;
import com.cvshealth.cap.registry.config.SlackClientConfig;
import com.cvshealth.cap.registry.model.SlackApiCheckedFunction;
import com.cvshealth.cap.registry.model.ModelPair;
import com.cvshealth.cap.registry.model.exception.SlackServiceException;
import com.slack.api.Slack;
import com.slack.api.SlackConfig;
import com.slack.api.methods.*;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.conversations.ConversationsInfoRequest;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.conversations.ConversationsInfoResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.ConversationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlackService {

    private final SlackClientConfig slackClientConfig;

    private static final String ACTION_SLACK_CALL = "slackCall";
    private static final String ACTION_SLACK_ASYNC_CALL = "slackAsyncCall";
    private static final int CONVERSION_LIST_BATCH_SIZE = 999;
    public static final String CHANNEL_NOT_FOUND = "channel_not_found";

    @PostConstruct
    public void init() {
        if (slackClientConfig.isEnabled()) {
            Validate.notNull(slackClientConfig.getToken(), "app.slack.token config is required");
        }
    }

    private Slack getSlack() {
        if (slackClientConfig.getMethodsEndpointUrlPrefix() != null) {
            SlackConfig config = new SlackConfig();
            config.setMethodsEndpointUrlPrefix(slackClientConfig.getMethodsEndpointUrlPrefix());
            return Slack.getInstance(config);
        }
        return Slack.getInstance();
    }

    MethodsClient methodsClient() {
        return getSlack().methods(slackClientConfig.getToken());
    }

    AsyncMethodsClient asyncMethodsClient() {
        return getSlack().methodsAsync(slackClientConfig.getToken());
    }

    /**
     * Sends Slack message to the given channel ID
     * Bot must have been invited to a private channel before being able to send messages.
     */
    public void sendMessageToChannel(String channelId, String channelName, String message) throws SlackServiceException {
        log.info("Sending Slack message to channel {}[{}]", channelName, channelId);
        SlackApiCheckedFunction<SlackApiRequest, SlackApiTextResponse> slackCall = request ->
                methodsClient().chatPostMessage((ChatPostMessageRequest) request);

        ChatPostMessageRequest request = ChatPostMessageRequest.builder().channel(channelId).text(message).build();
        SlackApiTextResponse response = slackRetriableApiCall(request, slackCall, slackClientConfig.getPostMessageRetries());
        if (response != null && response.isOk()) {
            log.info("Successfully sent Slack message to channel {}[{}]", channelName, channelId);
        }
    }

    /**
     * Sends Slack message asynchronously to the given channel ID
     */
    public CompletableFuture<ChatPostMessageResponse> sendAsyncMessageToChannel(String channelId, String channelName, String message) {
        log.info("[Async] Sending Slack message to channel {}[{}]", channelName, channelId);
        Function<SlackApiRequest, CompletableFuture<ChatPostMessageResponse>> slackCall = request ->
                asyncMethodsClient().chatPostMessage((ChatPostMessageRequest)request);
        ChatPostMessageRequest request = ChatPostMessageRequest.builder().channel(channelId).text(message).build();
        return slackRetriableAsyncApiCall(request, slackCall, slackClientConfig.getPostMessageRetries());
    }

    public Map<String, Conversation> getChannels() throws SlackServiceException {
        Map<String, Conversation> channels = new HashMap<>();
        String cursor = null;
        do {
            ModelPair<String, Map<String, Conversation>> result = callConversionInfo(cursor);
            channels.putAll(result.getSecond());
            cursor = result.getFirst();
        } while (StringUtils.isNotBlank(cursor));
        return channels;
    }

    private ModelPair<String, Map<String, Conversation>> callConversionInfo(String cursor) throws SlackServiceException {
        if (StringUtils.isNotBlank(cursor)) {
            try {
                Thread.sleep(1000L); // force delay for Web API Tier 2 rate limiting
            } catch (InterruptedException ignored) {}
        }

        SlackApiCheckedFunction<SlackApiRequest, SlackApiTextResponse> func = request ->
                methodsClient().conversationsList((ConversationsListRequest)request);
        ConversationsListRequest request = ConversationsListRequest.builder()
                .token(slackClientConfig.getToken())
                .types(List.of(ConversationType.PRIVATE_CHANNEL))
                .excludeArchived(true)
                .cursor(cursor)
                .limit(CONVERSION_LIST_BATCH_SIZE).build();
        SlackApiTextResponse response = slackRetriableApiCall(request, func, slackClientConfig.getConversationListRetries());
        if (response != null && response.isOk()) {
            ConversationsListResponse conversationsListResponse = (ConversationsListResponse) response;
            Map<String, Conversation> channels = new HashMap<>();
            String nextCursor = conversationsListResponse.getResponseMetadata().getNextCursor();
            conversationsListResponse.getChannels().forEach(channel -> {
                channels.put(channel.getName(), channel);
            });
            return new ModelPair<>(nextCursor, channels);
        }
        return null;
    }

    @Cacheable(value = CacheConfig.CACHE_SLACK_CHANNEL_INFO)
    public Conversation getChannelInfo(String channelId) throws SlackServiceException {
        try {
            log.info("Get Slack channel info ID[{}]", channelId);
            SlackApiCheckedFunction<SlackApiRequest, SlackApiTextResponse> slackCall = request ->
                    methodsClient().conversationsInfo((ConversationsInfoRequest) request);

            ConversationsInfoRequest request = ConversationsInfoRequest.builder().channel(channelId).token(slackClientConfig.getToken())
                    .includeNumMembers(true).build();
            SlackApiTextResponse response = slackApiCall(request, slackCall);
            if (response != null && response.isOk()) {
                ConversationsInfoResponse infoResponse = (ConversationsInfoResponse) response;
                log.info("Successfully retrieved Slack channel info {}[{}]", infoResponse.getChannel().getName(), channelId);
                return infoResponse.getChannel();
            }
        } catch (SlackServiceException e) {
            if (CHANNEL_NOT_FOUND.equals(e.getMessage())) {
                return null;
            }
            throw e;
        }
        return null;
    }

    private SlackApiTextResponse slackApiCall(SlackApiRequest request, SlackApiCheckedFunction<SlackApiRequest, SlackApiTextResponse> func) throws SlackServiceException {
        return slackRetriableApiCall(request, func, 0);
    }

    private SlackApiTextResponse slackRetriableApiCall(SlackApiRequest request, SlackApiCheckedFunction<SlackApiRequest, SlackApiTextResponse> func, int retries) throws SlackServiceException {
        if (!slackClientConfig.isEnabled()) {
            return null;
        }
        try {
            SlackApiTextResponse response = func.apply(request);
            if (response.isOk()) {
                if (response.getWarning() != null) {
                    log.info("Slack call warning[{}]", response.getWarning());
                }
            } else {
                String errorMessage = response.getError();
                if (retries > 0) {
                    Integer retriableInSeconds = retriableValue(response);
                    if (retriableInSeconds != null) {
                        return slackApiRetryCall(request, func, retries, retriableInSeconds);
                    }
                }
                throw new SlackServiceException(HttpStatus.OK, ACTION_SLACK_CALL, errorMessage);
            }
            return response;
        } catch (SlackApiException se) {
            log.error("Slack call error", se);
            if (retries > 0) {
                Integer retriableInSeconds = retriableValue(se.getResponse());
                if (retriableInSeconds != null) {
                    return slackApiRetryCall(request, func, retries, retriableInSeconds);
                }
            }
            HttpStatus status = HttpStatus.resolve(se.getResponse().code());
            String errorMessage = se.getError() != null ? se.getError().getError() : se.getMessage();
            throw new SlackServiceException(status, ACTION_SLACK_CALL, errorMessage);
        } catch (IOException e) {
            log.error("Slack call error", e);
            throw new SlackServiceException(HttpStatus.INTERNAL_SERVER_ERROR, ACTION_SLACK_CALL, e.getMessage());
        }
    }

    private SlackApiTextResponse slackApiRetryCall(SlackApiRequest request, SlackApiCheckedFunction<SlackApiRequest, SlackApiTextResponse> func, int retries, Integer retriableInSeconds) throws SlackServiceException {
        try {
            Thread.sleep(1000L * retriableInSeconds);
            log.warn("Slack call error. Retrying... attempts remaining {}", retries);
            return slackRetriableApiCall(request, func, retries - 1);
        } catch (InterruptedException e) {
            log.error("Slack call error", e);
            throw new SlackServiceException(HttpStatus.OK, ACTION_SLACK_CALL, e.getMessage());
        }
    }

    private <T> CompletableFuture<T> slackRetriableAsyncApiCall(SlackApiRequest request, Function<SlackApiRequest, CompletableFuture<T>> func, int retries) {
        if (!slackClientConfig.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        return func.apply(request)
                .thenComposeAsync(response -> {
                    if (response instanceof SlackApiTextResponse slackApiTextResponse) {
                        if (slackApiTextResponse.isOk()) {
                            if (slackApiTextResponse.getWarning() != null) {
                                log.info("Slack call warning[{}]", slackApiTextResponse.getWarning());
                            }
                        } else {
                            String errorMessage = slackApiTextResponse.getError();
                            log.error("Slack async call error[{}]", errorMessage);
                            if (retries > 0) {
                                Integer retriableInSeconds = retriableValue(slackApiTextResponse);
                                if (retriableInSeconds != null) {
                                    return slackAsyncApiRetryCall(request, func, retries, retriableInSeconds);
                                }
                            }
                            CompletableFuture<T> failedFuture = new CompletableFuture<>();
                            failedFuture.completeExceptionally(new SlackServiceException(HttpStatus.OK, ACTION_SLACK_ASYNC_CALL, errorMessage));
                            return failedFuture;
                        }
                    }
                    CompletableFuture<T> completedFuture = new CompletableFuture<>();
                    completedFuture.complete(response);
                    return completedFuture;
                })
                .exceptionallyComposeAsync(ex -> {
                    log.error("Slack async call error.", ex);
                    if (retries > 0 && ex.getCause() instanceof SlackApiException slackApiException) {
                        Integer retriableInSeconds = retriableValue(slackApiException.getResponse());
                        if (retriableInSeconds != null) {
                            return slackAsyncApiRetryCall(request, func, retries, retriableInSeconds);
                        }
                    }
                    CompletableFuture<T> failedFuture = new CompletableFuture<>();
                    failedFuture.completeExceptionally(ex);
                    return failedFuture;
                });
    }

    private <T> CompletableFuture<T> slackAsyncApiRetryCall(SlackApiRequest request, Function<SlackApiRequest, CompletableFuture<T>> func, int retries, Integer retriableInSeconds) {
        try {
            Thread.sleep(1000L * retriableInSeconds);
            log.warn("Slack async call error. Retrying... attempts remaining {}", retries);
            return slackRetriableAsyncApiCall(request, func, retries - 1);
        } catch (InterruptedException e) {
            log.error("Slack async call error", e);
            CompletableFuture<T> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new SlackServiceException(HttpStatus.OK, ACTION_SLACK_ASYNC_CALL, e.getMessage()));
            return failedFuture;
        }
    }

    private Integer retriableValue(Response response) {
        if (response.code() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            String headerValue = response.header("Retry-After");
            if (headerValue != null) {
                return Integer.valueOf(headerValue);
            }
        }
        return null;
    }

    private Integer retriableValue(SlackApiTextResponse response) {
        if ("ratelimited".equals(response.getError()) || "rate_limited".equals(response.getError())) {
            Map<String, List<String>> headers = response.getHttpResponseHeaders();
            if (!CollectionUtils.isEmpty(headers)) {
                List<String> values = headers.get("Retry-After");
                if (!CollectionUtils.isEmpty(values)) {
                    return Integer.valueOf(values.get(0));
                }
            }
        }
        return null;
    }
}
