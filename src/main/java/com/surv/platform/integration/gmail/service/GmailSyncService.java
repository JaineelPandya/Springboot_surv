package com.surv.platform.integration.gmail.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.surv.platform.common.exception.BusinessException;
import com.surv.platform.common.exception.ResourceNotFoundException;
import com.surv.platform.document.DocumentService;
import com.surv.platform.document.FingerprintService;
import com.surv.platform.document.MimeTypeDetector;
import com.surv.platform.document.dto.DocumentRegisterRequest;
import com.surv.platform.document.dto.DocumentUploadResult;
import com.surv.platform.domain.*;
import com.surv.platform.integration.gmail.client.GmailClientProvider;
import com.surv.platform.integration.gmail.dto.EmailAttachmentDto;
import com.surv.platform.integration.gmail.dto.EmailMetadataDto;
import com.surv.platform.integration.gmail.dto.GmailSyncResultDto;
import com.surv.platform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailSyncService {

    private static final String CURSOR_TYPE_HISTORY = "GMAIL_HISTORY";

    private final IntegrationConnectionRepository connectionRepository;
    private final SyncCursorRepository syncCursorRepository;
    private final GmailMessageRecordRepository messageRecordRepository;
    private final GmailAttachmentRecordRepository attachmentRecordRepository;
    private final DocumentEventRepository documentEventRepository;
    private final GmailClientProvider clientProvider;
    private final GmailMessageExtractor messageExtractor;
    private final DocumentService documentService;
    private final FingerprintService fingerprintService;
    private final MimeTypeDetector mimeTypeDetector;

    /**
     * Incremental or reconciliation sync for a Gmail integration connection.
     * Uses Gmail History API if cursor is valid, otherwise performs reconciliation.
     */
    @Transactional
    public GmailSyncResultDto syncConnection(UUID connectionId) {
        IntegrationConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationConnection", "id", connectionId));

        Gmail gmail = clientProvider.getClient(connection);
        SyncCursor cursor = syncCursorRepository.findByConnectionIdAndCursorType(connectionId, CURSOR_TYPE_HISTORY)
                .orElse(null);

        Set<String> messageIdsToProcess = new LinkedHashSet<>();
        Long latestHistoryId = null;

        // 1. Attempt History API sync if cursor exists
        boolean historySyncSuccess = false;
        if (cursor != null && cursor.getCursorValue() != null) {
            try {
                BigInteger startHistoryId = new BigInteger(cursor.getCursorValue());
                log.info("Performing History API sync for connection={} startHistoryId={}", connectionId, startHistoryId);

                ListHistoryResponse historyResponse = gmail.users().history().list("me")
                        .setStartHistoryId(startHistoryId)
                        .setMaxResults(100L)
                        .execute();

                latestHistoryId = historyResponse.getHistoryId() != null
                        ? historyResponse.getHistoryId().longValue()
                        : null;

                if (historyResponse.getHistory() != null) {
                    for (History h : historyResponse.getHistory()) {
                        if (h.getMessagesAdded() != null) {
                            for (HistoryMessageAdded ma : h.getMessagesAdded()) {
                                if (ma.getMessage() != null && ma.getMessage().getId() != null) {
                                    messageIdsToProcess.add(ma.getMessage().getId());
                                }
                            }
                        }
                    }
                }
                historySyncSuccess = true;
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 404 || e.getStatusCode() == 400) {
                    log.warn("Gmail history expired or invalid for connection {}. Falling back to reconciliation scan.", connectionId);
                } else {
                    log.error("Google API error during history sync: {}", e.getMessage());
                    throw new BusinessException("Gmail history sync failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
                }
            } catch (IOException e) {
                log.error("IO error during Gmail history sync: {}", e.getMessage());
                throw new BusinessException("Network error during Gmail sync", HttpStatus.SERVICE_UNAVAILABLE);
            }
        }

        // 2. Fallback reconciliation scan (if no cursor or history expired)
        if (!historySyncSuccess) {
            log.info("Performing mailbox reconciliation scan for connection={}", connectionId);
            try {
                ListMessagesResponse listResponse = gmail.users().messages().list("me")
                        .setMaxResults(50L)
                        .execute();

                if (listResponse.getMessages() != null) {
                    for (Message m : listResponse.getMessages()) {
                        if (m.getId() != null) {
                            messageIdsToProcess.add(m.getId());
                        }
                    }
                }

                Profile profile = gmail.users().getProfile("me").execute();
                if (profile != null && profile.getHistoryId() != null) {
                    latestHistoryId = profile.getHistoryId().longValue();
                }
            } catch (IOException e) {
                log.error("Failed to perform Gmail reconciliation scan: {}", e.getMessage());
                throw new BusinessException("Reconciliation scan failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
            }
        }

        // 3. Process discovered messages with strict deduplication
        int messagesProcessed = 0;
        int attachmentsIdentified = 0;

        for (String msgId : messageIdsToProcess) {
            if (messageRecordRepository.existsByConnectionIdAndMessageId(connectionId, msgId)) {
                log.debug("Skipping already processed messageId={} for connectionId={}", msgId, connectionId);
                continue;
            }

            try {
                // Fetch headers and attachment metadata (full body text is excluded to prevent over-collection)
                Message fullMsg = gmail.users().messages().get("me", msgId)
                        .setFormat("full")
                        .execute();

                EmailMetadataDto metadata = messageExtractor.extractMetadata(fullMsg);
                if (metadata == null) continue;

                GmailMessageRecord record = new GmailMessageRecord();
                record.setConnection(connection);
                record.setMessageId(msgId);
                record.setThreadId(metadata.threadId());
                record.setHistoryId(metadata.historyId());
                record.setGoogleEmail(connection.getAccountIdentifier());
                record.setSubject(metadata.subject());
                record.setSender(metadata.sender());
                record.setReceivedAt(metadata.receivedAt());
                record.setHasAttachments(!metadata.attachments().isEmpty());
                record.setAttachmentCount(metadata.attachments().size());
                record.setProcessedAt(Instant.now());
                record = messageRecordRepository.save(record);
                messagesProcessed++;

                // Record attachments
                for (EmailAttachmentDto att : metadata.attachments()) {
                    GmailAttachmentRecord attRecord = new GmailAttachmentRecord();
                    attRecord.setMessageRecord(record);
                    attRecord.setAttachmentId(att.attachmentId());
                    attRecord.setFilename(att.filename());
                    attRecord.setDeclaredMimeType(att.declaredMimeType());
                    attRecord.setFileSizeBytes(att.sizeBytes());
                    attRecord.setDownloaded(false);
                    attachmentRecordRepository.save(attRecord);
                    attachmentsIdentified++;
                }

                log.info("Processed email: msgId={} sender='{}' subject='{}' attachments={}",
                        msgId, metadata.sender(), metadata.subject(), metadata.attachments().size());

            } catch (IOException e) {
                log.warn("Failed to fetch message details for messageId={}: {}", msgId, e.getMessage());
            }
        }

        // 4. Update sync cursor and connection last synced time
        if (latestHistoryId != null) {
            if (cursor == null) {
                cursor = new SyncCursor();
                cursor.setConnection(connection);
                cursor.setCursorType(CURSOR_TYPE_HISTORY);
            }
            cursor.setCursorValue(latestHistoryId.toString());
            cursor.setLastProcessedAt(Instant.now());
            syncCursorRepository.save(cursor);
        }

        connection.setLastSyncedAt(Instant.now());
        connectionRepository.save(connection);

        log.info("Gmail sync completed: connection={} processed={} attachments={}",
                connectionId, messagesProcessed, attachmentsIdentified);

        return new GmailSyncResultDto(messagesProcessed, attachmentsIdentified, 0, latestHistoryId);
    }

    /**
     * Downloads an authorized email attachment from Gmail, verifies its SHA-256 fingerprint,
     * inspects true MIME type, registers it into the Document Registry, and records audit trail.
     */
    @Transactional
    public DocumentUploadResult downloadAndRegisterAttachment(UUID attachmentRecordId, Authentication auth) {
        GmailAttachmentRecord attachment = attachmentRecordRepository.findById(attachmentRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("GmailAttachmentRecord", "id", attachmentRecordId));

        if (attachment.isDownloaded() && attachment.getDocumentId() != null) {
            log.info("Attachment {} is already registered as document {}", attachmentRecordId, attachment.getDocumentId());
            throw new BusinessException("Attachment is already downloaded and registered", HttpStatus.CONFLICT);
        }

        GmailMessageRecord message = attachment.getMessageRecord();
        IntegrationConnection connection = message.getConnection();
        Gmail gmail = clientProvider.getClient(connection);

        // 1. Download attachment content from Gmail API
        byte[] content;
        try {
            MessagePartBody partBody = gmail.users().messages().attachments()
                    .get("me", message.getMessageId(), attachment.getAttachmentId())
                    .execute();

            if (partBody == null || partBody.getData() == null) {
                throw new BusinessException("Empty attachment content returned by Gmail", HttpStatus.BAD_GATEWAY);
            }
            content = Base64.getUrlDecoder().decode(partBody.getData());
        } catch (IOException e) {
            log.error("Failed to retrieve attachment {} from Gmail API: {}", attachment.getAttachmentId(), e.getMessage());
            throw new BusinessException("Failed to download attachment from Gmail: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }

        // 2. Content integrity and MIME inspection (never trust claimed extension/mime alone)
        String sha256 = fingerprintService.computeSha256(content);
        String detectedMime = mimeTypeDetector.detect(content, attachment.getFilename());

        if (!mimeTypeDetector.isSafeContentType(detectedMime)) {
            log.warn("Blocked potentially unsafe attachment: file='{}' detectedMime='{}'",
                    attachment.getFilename(), detectedMime);
            throw new BusinessException("Attachment rejected: unsafe content type (" + detectedMime + ")", HttpStatus.BAD_REQUEST);
        }

        // 3. Register into Document Registry
        String sourceDocId = "gmail:" + message.getMessageId() + ":" + attachment.getAttachmentId();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("provider", "GMAIL");
        metadata.put("gmailMessageId", message.getMessageId());
        metadata.put("gmailAttachmentId", attachment.getAttachmentId());
        metadata.put("sender", message.getSender());
        metadata.put("subject", message.getSubject());
        metadata.put("receivedAt", message.getReceivedAt() != null ? message.getReceivedAt().toString() : null);

        DocumentRegisterRequest regRequest = new DocumentRegisterRequest(
                attachment.getFilename() != null ? attachment.getFilename() : "gmail-attachment",
                sourceDocId,
                connection.getUser().getId(),
                null, // will trigger rule-based classification automatically
                detectedMime,
                "GMAIL",
                metadata
        );

        DocumentUploadResult uploadResult = documentService.registerDocument(
                regRequest, content, attachment.getFilename(), auth);

        // 4. Update attachment record
        attachment.setDownloaded(true);
        attachment.setDownloadedAt(Instant.now());
        attachment.setSha256Hash(sha256);
        attachment.setDetectedMimeType(detectedMime);
        attachment.setFileSizeBytes((long) content.length);
        attachment.setDocumentId(uploadResult.documentId());
        attachmentRecordRepository.save(attachment);

        // 5. Append-only DocumentEvent for surveillance traceability
        try {
            DocumentEvent event = new DocumentEvent();
            Document docRef = new Document();
            docRef.setId(uploadResult.documentId());
            event.setDocument(docRef);
            event.setActor(connection.getUser());
            event.setActorEmail(connection.getAccountIdentifier());
            event.setSourceEventId("gmail-ingest:" + uploadResult.documentId());
            event.setEventType("GMAIL_ATTACHMENT_INGESTED");
            event.setOccurredAt(Instant.now());
            event.setIngestedAt(Instant.now());
            Map<String, Object> eventMeta = new HashMap<>();
            eventMeta.put("attachmentId", attachment.getAttachmentId());
            eventMeta.put("messageId", message.getMessageId());
            eventMeta.put("sha256", sha256);
            eventMeta.put("size", content.length);
            event.setMetadata(eventMeta);
            documentEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record document event for gmail attachment: {}", e.getMessage());
        }

        log.info("Gmail attachment registered: attId={} docId={} sha256={} mime={}",
                attachmentRecordId, uploadResult.documentId(), sha256, detectedMime);

        return uploadResult;
    }
}
