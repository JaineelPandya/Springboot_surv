package com.surv.platform.integration.gmail.service;

import com.surv.platform.config.GmailProperties;
import com.surv.platform.domain.GmailWatch;
import com.surv.platform.domain.IntegrationConnection;
import com.surv.platform.repository.GmailWatchRepository;
import com.surv.platform.repository.IntegrationConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled background tasks for:
 * 1. Automatic renewal of Gmail push watches before expiration (Google watches expire after 7 days).
 * 2. Periodic reconciliation sync in case Pub/Sub notifications are delayed or lost.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmailReconciliationScheduler {

    private final GmailProperties gmailProperties;
    private final GmailWatchRepository watchRepository;
    private final GmailWatchService watchService;
    private final IntegrationConnectionRepository connectionRepository;
    private final GmailSyncService syncService;

    /**
     * Runs every 4 hours to renew watches expiring within the configured threshold (default 24 hours).
     */
    @Scheduled(cron = "${app.google.gmail.watch-renewal-cron:0 0 */4 * * *}")
    public void renewExpiringWatches() {
        if (!gmailProperties.isAutoRenewWatches()) {
            return;
        }

        Instant threshold = Instant.now().plus(gmailProperties.getWatchRenewalThresholdHours(), ChronoUnit.HOURS);
        List<GmailWatch> expiringWatches = watchRepository.findByActiveTrueAndExpiresAtBefore(threshold);

        log.info("Found {} Gmail watch(es) expiring before {}", expiringWatches.size(), threshold);
        for (GmailWatch watch : expiringWatches) {
            try {
                watchService.renewWatch(watch);
            } catch (Exception e) {
                log.error("Failed to renew expiring watch for email {}: {}", watch.getGoogleEmail(), e.getMessage());
            }
        }
    }

    /**
     * Periodic reconciliation runs daily (or as configured) to ensure no messages were dropped
     * if Google Cloud Pub/Sub encountered transient connectivity issues.
     */
    @Scheduled(cron = "${app.google.gmail.reconciliation-cron:0 0 2 * * *}")
    public void runPeriodicReconciliation() {
        if (!gmailProperties.isPeriodicReconciliationEnabled()) {
            return;
        }

        log.info("Starting scheduled periodic Gmail mailbox reconciliation...");
        List<IntegrationConnection> activeConnections = connectionRepository.findByStatus(IntegrationConnection.ConnectionStatus.ACTIVE);

        for (IntegrationConnection connection : activeConnections) {
            if ("GMAIL".equalsIgnoreCase(connection.getProvider())) {
                try {
                    syncService.syncConnection(connection.getId());
                } catch (Exception e) {
                    log.warn("Periodic reconciliation failed for connection {}: {}", connection.getId(), e.getMessage());
                }
            }
        }
        log.info("Completed scheduled periodic Gmail mailbox reconciliation");
    }
}
