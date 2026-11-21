package com.surv.platform.integration.gmail.service;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.surv.platform.integration.gmail.dto.EmailAttachmentDto;
import com.surv.platform.integration.gmail.dto.EmailMetadataDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Extracts authorized email metadata, headers and attachment references from Gmail Message model.
 * Important:
 * - Does NOT collect email body/text content beyond authorized metadata scope.
 * - Does NOT interpret mailbox read status as proof that an attachment was opened.
 */
@Slf4j
@Component
public class GmailMessageExtractor {

    public EmailMetadataDto extractMetadata(Message message) {
        if (message == null) {
            return null;
        }

        String messageId = message.getId();
        String threadId = message.getThreadId();
        Long historyId = message.getHistoryId() != null ? message.getHistoryId().longValue() : null;
        Instant receivedAt = message.getInternalDate() != null
                ? Instant.ofEpochMilli(message.getInternalDate())
                : Instant.now();

        String subject = null;
        String sender = null;
        List<String> toRecipients = new ArrayList<>();
        List<String> ccRecipients = new ArrayList<>();
        List<String> bccRecipients = new ArrayList<>();

        MessagePart payload = message.getPayload();
        if (payload != null && payload.getHeaders() != null) {
            for (MessagePartHeader header : payload.getHeaders()) {
                String name = header.getName();
                String value = header.getValue();
                if (name == null || value == null) continue;

                switch (name.toLowerCase()) {
                    case "subject" -> subject = value;
                    case "from" -> sender = value;
                    case "to" -> toRecipients.addAll(parseAddressList(value));
                    case "cc" -> ccRecipients.addAll(parseAddressList(value));
                    case "bcc" -> bccRecipients.addAll(parseAddressList(value));
                    case "date" -> {
                        // Keep internalDate as primary canonical timestamp, fallback to Date header if needed
                    }
                }
            }
        }

        List<EmailAttachmentDto> attachments = new ArrayList<>();
        if (payload != null) {
            findAttachments(payload, attachments);
        }

        return new EmailMetadataDto(
                messageId,
                threadId,
                historyId,
                subject,
                sender,
                toRecipients,
                ccRecipients,
                bccRecipients,
                receivedAt,
                attachments
        );
    }

    private void findAttachments(MessagePart part, List<EmailAttachmentDto> list) {
        if (part == null) return;

        String filename = part.getFilename();
        if (filename != null && !filename.isBlank() && part.getBody() != null) {
            String attachmentId = part.getBody().getAttachmentId();
            Long size = part.getBody().getSize() != null ? part.getBody().getSize().longValue() : 0L;
            String mimeType = part.getMimeType() != null ? part.getMimeType() : "application/octet-stream";

            if (attachmentId != null && !attachmentId.isBlank()) {
                list.add(new EmailAttachmentDto(attachmentId, filename, mimeType, size));
            }
        }

        // Recursively inspect multi-part structures
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                findAttachments(subPart, list);
            }
        }
    }

    private List<String> parseAddressList(String rawAddresses) {
        if (rawAddresses == null || rawAddresses.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rawAddresses.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
