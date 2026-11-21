package com.surv.platform.integration.gmail.dto;

public record EmailAttachmentDto(
        String attachmentId,
        String filename,
        String declaredMimeType,
        Long sizeBytes
) {}
