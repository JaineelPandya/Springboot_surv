package com.surv.platform.integration.gmail.dto;

import java.time.Instant;
import java.util.UUID;

public record GmailConnectionDto(
        UUID connectionId,
        UUID userId,
        String googleEmail,
        String provider,
        String status,
        Instant lastSyncedAt,
        Instant watchExpiresAt,
        boolean watchActive
) {}
