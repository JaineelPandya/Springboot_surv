package com.surv.platform.document.dto;

import com.surv.platform.domain.Document;

import java.time.Instant;
import java.util.UUID;

public record DocumentUploadResult(
        UUID documentId,
        UUID versionId,
        String sha256Hash,
        String detectedMimeType,
        Document.SensitivityLevel sensitivityLevel,
        String classificationMethod,
        long fileSizeBytes,
        Instant registeredAt
) {}
