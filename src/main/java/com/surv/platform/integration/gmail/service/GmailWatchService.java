package com.surv.platform.integration.gmail.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.WatchRequest;
import com.google.api.services.gmail.model.WatchResponse;
import com.surv.platform.common.exception.BusinessException;
import com.surv.platform.common.exception.ResourceNotFoundException;
import com.surv.platform.config.GmailProperties;
import com.surv.platform.domain.GmailWatch;
import com.surv.platform.domain.IntegrationConnection;
import com.surv.platform.integration.gmail.client.GmailClientProvider;
import com.surv.platform.integration.gmail.dto.PubSubNotificationRequest;
import com.surv.platform.repository.GmailWatchRepository;
import com.surv.platform.repository.IntegrationConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailWatchService {

    private final GmailProperties gmailProperties;
    private final IntegrationConnectionRepository connectionRepository;
    private final GmailWatchRepository watchRepository;
    private final GmailClientProvider clientProvider;
    private final GmailSyncService syncService;
    private final ObjectMapper objectMapper;

    /**
     * Registers a push-notification watch with Google Cloud Pub/Sub for a Gmail connection.
     * Google will post mailbox change notifications to the configured Pub/Sub topic.
     */
    @Transactional
    public GmailWatch registerWatch(UUID connectionId) {
        IntegrationConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationConnection", "id", connectionId));

        Gmail gmail = clientProvider.getClient(connection);
        String topicName = gmailProperties.getPubSubTopicName();

        try {
            WatchRequest watchRequest = new WatchRequest()
                    .setTopicName(topicName)
                    .setLabelIds(List.of("INBOX"));

            log.info("Registering Gmail watch for connection={} email={} topic={}",
                    connectionId, connection.getAccountIdentifier(), topicName);

            WatchResponse watchResponse = gmail.users().watch("me", watchRequest).execute();

            Long historyId = watchResponse.getHistoryId() != null
                    ? watchResponse.getHistoryId().longValue()
                    : 0L;

            Instant expiresAt = watchResponse.getExpiration() != null
                    ? Instant.ofEpochMilli(watchResponse.getExpiration())
                    : Instant.now().plusSeconds(7 * 86400); // 7-day default

            GmailWatch watch = watchRepository.findByConnectionIdAndActiveTrue(connectionId)
                    .orElseGet(() -> {
                        GmailWatch w = new GmailWatch();
                        w.setConnection(connection);
                        return w;
                    });

            watch.setGoogleEmail(connection.getAccountIdentifier());
            watch.setTopicName(topicName);
            watch.setHistoryId(historyId);
            watch.setExpiresAt(expiresAt);
            watch.setActive(true);
            watch.setRegisteredAt(Instant.now());

            log.info("Gmail watch successfully registered: historyId={} expiresAt={}", historyId, expiresAt);
            return watchRepository.save(watch);

        } catch (IOException e) {
            log.error("Failed to register Gmail watch: {}", e.getMessage());
            throw new BusinessException("Failed to register Gmail watch with Google: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * Proactively renews an existing Gmail watch before it expires.
     */
    @Transactional
    public void renewWatch(GmailWatch watch) {
        try {
            log.info("Proactively renewing watch for connection={} email={}",
                    watch.getConnection().getId(), watch.getGoogleEmail());
            registerWatch(watch.getConnection().getId());
        } catch (Exception e) {
            log.warn("Failed to renew watch for connection {}: {}", watch.getConnection().getId(), e.getMessage());
        }
    }

    /**
     * Processes incoming Google Cloud Pub/Sub push notifications.
     * Decodes Base64 data: { "emailAddress": "...", "historyId": "..." }
     */
    @Transactional
    public void handlePubSubNotification(PubSubNotificationRequest request) {
        if (request == null || request.message() == null || request.message().data() == null) {
            log.warn("Received empty Pub/Sub push notification");
            return;
        }

        try {
            String decodedPayload = new String(Base64.getDecoder().decode(request.message().data()), StandardCharsets.UTF_8);
            log.info("Received Pub/Sub notification: payload={}", decodedPayload);

            JsonNode jsonNode = objectMapper.readTree(decodedPayload);
            String emailAddress = jsonNode.path("emailAddress").asText();
            long notificationHistoryId = jsonNode.path("historyId").asLong(0L);

            if (emailAddress == null || emailAddress.isBlank()) {
                log.warn("Pub/Sub notification missing emailAddress");
                return;
            }

            var activeWatch = watchRepository.findByGoogleEmailAndActiveTrue(emailAddress);
            if (activeWatch.isEmpty()) {
                log.warn("No active Gmail watch registered for email: {}", emailAddress);
                return;
            }

            IntegrationConnection connection = activeWatch.get().getConnection();
            log.info("Triggering sync for connection={} historyId={}", connection.getId(), notificationHistoryId);
            syncService.syncConnection(connection.getId());

        } catch (Exception e) {
            log.error("Error processing Pub/Sub notification: {}", e.getMessage(), e);
        }
    }
}
