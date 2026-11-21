package com.surv.platform.integration.gmail.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PubSubNotificationRequest(
        PubSubMessage message,
        String subscription
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PubSubMessage(
            String data,
            Map<String, String> attributes,
            String messageId,
            String publishTime
    ) {}
}
