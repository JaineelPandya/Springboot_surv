package com.surv.platform.integration.gmail.dto;

import java.util.List;

public record EmailMetadataDto(
        String messageId,
        String threadId,
        Long historyId,
        String subject,
        String sender,
        List<String> recipientsTo,
        List<String> recipientsCc,
        List<String> recipientsBcc,
        java.time.Instant receivedAt,
        List<EmailAttachmentDto> attachments
) {}
