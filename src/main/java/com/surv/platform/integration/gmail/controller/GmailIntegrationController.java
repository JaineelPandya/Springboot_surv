package com.surv.platform.integration.gmail.controller;

import com.surv.platform.common.response.ApiResponse;
import com.surv.platform.document.dto.DocumentUploadResult;
import com.surv.platform.domain.AppAccount;
import com.surv.platform.domain.GmailWatch;
import com.surv.platform.domain.IntegrationConnection;
import com.surv.platform.integration.gmail.dto.GmailAuthUrlResponse;
import com.surv.platform.integration.gmail.dto.GmailConnectionDto;
import com.surv.platform.integration.gmail.dto.GmailSyncResultDto;
import com.surv.platform.integration.gmail.service.GmailOAuthService;
import com.surv.platform.integration.gmail.service.GmailSyncService;
import com.surv.platform.integration.gmail.service.GmailWatchService;
import com.surv.platform.repository.AppAccountRepository;
import com.surv.platform.repository.IntegrationConnectionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Gmail Integration", description = "OAuth authorization, mailbox watch, sync, and attachment ingestion")
@RestController
@RequestMapping("/api/v1/integrations/gmail")
@RequiredArgsConstructor
public class GmailIntegrationController {

    private final GmailOAuthService oAuthService;
    private final GmailSyncService syncService;
    private final GmailWatchService watchService;
    private final IntegrationConnectionRepository connectionRepository;
    private final AppAccountRepository appAccountRepository;

    @Operation(summary = "Generate Google OAuth2 authorization URL")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/oauth2/authorize")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<GmailAuthUrlResponse>> getAuthorizationUrl(
            @RequestParam(required = false, defaultValue = "surv") String state,
            Authentication auth) {
        AppAccount account = appAccountRepository.findByUsername(auth.getName()).orElseThrow();
        String url = oAuthService.buildAuthorizationUrl(account.getUser().getId(), state);
        return ResponseEntity.ok(ApiResponse.success(new GmailAuthUrlResponse(url, state)));
    }

    @Operation(summary = "OAuth2 redirect callback from Google")
    @GetMapping("/oauth2/callback")
    public ResponseEntity<ApiResponse<GmailConnectionDto>> oauthCallback(
            @RequestParam String code,
            @RequestParam String state) {
        GmailConnectionDto dto = oAuthService.handleOAuthCallback(code, state);
        return ResponseEntity.ok(ApiResponse.success(dto, "Google Workspace account connected successfully"));
    }

    @Operation(summary = "List current user's connected Gmail accounts")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/connections")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<GmailConnectionDto>>> getConnections(Authentication auth) {
        AppAccount account = appAccountRepository.findByUsername(auth.getName()).orElseThrow();
        List<IntegrationConnection> list = connectionRepository.findByUserIdAndProvider(account.getUser().getId(), "GMAIL");
        List<GmailConnectionDto> dtos = list.stream().map(oAuthService::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @Operation(summary = "Register Google Cloud Pub/Sub mailbox watch on a connected Gmail account")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/connections/{connectionId}/watch")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<GmailWatchDto>> registerWatch(
            @PathVariable UUID connectionId,
            Authentication auth) {
        GmailWatch watch = watchService.registerWatch(connectionId);
        return ResponseEntity.ok(ApiResponse.success(new GmailWatchDto(
                watch.getId(),
                watch.getGoogleEmail(),
                watch.getTopicName(),
                watch.getHistoryId(),
                watch.getExpiresAt(),
                watch.isActive()
        ), "Gmail push watch registered successfully"));
    }

    @Operation(summary = "Trigger manual synchronization or reconciliation of mailbox")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/connections/{connectionId}/sync")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<GmailSyncResultDto>> triggerSync(
            @PathVariable UUID connectionId,
            Authentication auth) {
        GmailSyncResultDto result = syncService.syncConnection(connectionId);
        return ResponseEntity.ok(ApiResponse.success(result, "Mailbox synchronized successfully"));
    }

    @Operation(summary = "Download an authorized email attachment and register in Document Registry")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/attachments/{attachmentRecordId}/ingest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DocumentUploadResult>> ingestAttachment(
            @PathVariable UUID attachmentRecordId,
            Authentication auth) {
        DocumentUploadResult result = syncService.downloadAndRegisterAttachment(attachmentRecordId, auth);
        return ResponseEntity.ok(ApiResponse.success(result, "Attachment registered in Document Registry"));
    }

    public record GmailWatchDto(
            UUID watchId,
            String googleEmail,
            String topicName,
            Long historyId,
            java.time.Instant expiresAt,
            boolean active
    ) {}
}
