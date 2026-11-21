package com.surv.platform.document.dto;

import com.surv.platform.domain.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record DocumentRegisterRequest(
        @NotBlank @Size(max = 1000) String title,
        @NotBlank @Size(max = 500)  String sourceDocumentId,
        @NotNull UUID ownerId,
        Document.SensitivityLevel sensitivityLevel,
        @Size(max = 255) String mimeTypeHint,
        String provider,
        Map<String, Object> metadata
) {}
