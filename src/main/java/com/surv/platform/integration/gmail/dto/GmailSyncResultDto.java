package com.surv.platform.integration.gmail.dto;

public record GmailSyncResultDto(
        int messagesProcessed,
        int attachmentsIdentified,
        int attachmentsDownloaded,
        Long latestHistoryId
) {}
