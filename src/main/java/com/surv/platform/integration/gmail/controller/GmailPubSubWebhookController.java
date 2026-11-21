package com.surv.platform.integration.gmail.controller;

import com.surv.platform.integration.gmail.dto.PubSubNotificationRequest;
import com.surv.platform.integration.gmail.service.GmailWatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Gmail Webhook", description = "Receives Google Cloud Pub/Sub push notifications for mailbox events")
@RestController
@RequestMapping("/api/v1/integrations/gmail")
@RequiredArgsConstructor
public class GmailPubSubWebhookController {

    private final GmailWatchService watchService;

    @Operation(summary = "Google Cloud Pub/Sub push subscription webhook")
    @PostMapping("/webhook")
    public ResponseEntity<Void> receivePubSubNotification(@RequestBody PubSubNotificationRequest request) {
        log.debug("Received Google Cloud Pub/Sub notification webhook");
        watchService.handlePubSubNotification(request);
        // Pub/Sub push endpoints must return 200/204 to acknowledge receipt
        return ResponseEntity.ok().build();
    }
}
