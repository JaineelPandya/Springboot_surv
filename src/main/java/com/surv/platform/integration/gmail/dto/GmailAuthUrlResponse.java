package com.surv.platform.integration.gmail.dto;

public record GmailAuthUrlResponse(
        String authorizationUrl,
        String state
) {}
