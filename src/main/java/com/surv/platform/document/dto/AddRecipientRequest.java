package com.surv.platform.document.dto;

import com.surv.platform.domain.DocumentRecipient;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record AddRecipientRequest(
        @NotNull UUID userId,
        @NotNull DocumentRecipient.AccessLevel accessLevel,
        Instant expiresAt
) {}
