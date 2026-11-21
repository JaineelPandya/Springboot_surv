package com.surv.platform.integration.gmail.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surv.platform.common.exception.BusinessException;
import com.surv.platform.common.exception.ResourceNotFoundException;
import com.surv.platform.config.GmailProperties;
import com.surv.platform.domain.GmailOAuthToken;
import com.surv.platform.domain.IntegrationConnection;
import com.surv.platform.domain.User;
import com.surv.platform.integration.gmail.dto.GmailConnectionDto;
import com.surv.platform.integration.gmail.security.CredentialEncryptionService;
import com.surv.platform.repository.GmailOAuthTokenRepository;
import com.surv.platform.repository.GmailWatchRepository;
import com.surv.platform.repository.IntegrationConnectionRepository;
import com.surv.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailOAuthService {

    private static final String GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final GmailProperties gmailProperties;
    private final CredentialEncryptionService encryptionService;
    private final IntegrationConnectionRepository connectionRepository;
    private final GmailOAuthTokenRepository tokenRepository;
    private final GmailWatchRepository watchRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Builds the Google OAuth2 authorization URL with offline access to receive a refresh token.
     */
    public String buildAuthorizationUrl(UUID userId, String state) {
        String effectiveState = (state != null ? state : "") + ":" + userId.toString();
        return UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_ENDPOINT)
                .queryParam("client_id", gmailProperties.getClientId())
                .queryParam("redirect_uri", gmailProperties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", gmailProperties.getScopes()))
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", effectiveState)
                .build()
                .toUriString();
    }

    /**
     * Exchanges OAuth2 authorization code for tokens, encrypts them, and persists the integration connection.
     */
    @Transactional
    public GmailConnectionDto handleOAuthCallback(String code, String state) {
        UUID userId = extractUserIdFromState(state);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // 1. Exchange auth code for tokens
        JsonNode tokenResponse = exchangeCodeForTokens(code);
        String rawAccessToken = tokenResponse.path("access_token").asText();
        String rawRefreshToken = tokenResponse.path("refresh_token").asText();
        long expiresIn = tokenResponse.path("expires_in").asLong(3600);
        String grantedScope = tokenResponse.path("scope").asText(String.join(" ", gmailProperties.getScopes()));

        // 2. Fetch Google profile (email & sub)
        JsonNode userInfo = fetchGoogleUserInfo(rawAccessToken);
        String googleEmail = userInfo.path("email").asText();
        String googleSub = userInfo.path("sub").asText();

        log.info("Authorized Google Workspace connection for user={} googleEmail={}", user.getEmail(), googleEmail);

        // 3. Find or create IntegrationConnection
        IntegrationConnection connection = connectionRepository.findByUserIdAndProvider(userId, "GMAIL")
                .stream()
                .filter(c -> googleEmail.equalsIgnoreCase(c.getAccountIdentifier()))
                .findFirst()
                .orElseGet(() -> {
                    IntegrationConnection conn = new IntegrationConnection();
                    conn.setUser(user);
                    conn.setProvider("GMAIL");
                    conn.setAccountIdentifier(googleEmail);
                    conn.setStatus(IntegrationConnection.ConnectionStatus.ACTIVE);
                    Map<String, Object> cfg = new HashMap<>();
                    cfg.put("googleUserId", googleSub);
                    cfg.put("authorizedAt", Instant.now().toString());
                    conn.setConfig(cfg);
                    return connectionRepository.save(conn);
                });

        connection.setStatus(IntegrationConnection.ConnectionStatus.ACTIVE);
        connection.setErrorMessage(null);
        connectionRepository.save(connection);

        // 4. Save or update encrypted tokens
        GmailOAuthToken oauthToken = tokenRepository.findByConnectionId(connection.getId())
                .orElseGet(() -> {
                    GmailOAuthToken t = new GmailOAuthToken();
                    t.setConnection(connection);
                    return t;
                });

        oauthToken.setGoogleEmail(googleEmail);
        oauthToken.setGoogleUserId(googleSub);
        oauthToken.setEncryptedAccessToken(encryptionService.encrypt(rawAccessToken));
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            oauthToken.setEncryptedRefreshToken(encryptionService.encrypt(rawRefreshToken));
        }
        oauthToken.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn - 60)); // 60s buffer
        oauthToken.setGrantedScopes(grantedScope);
        tokenRepository.save(oauthToken);

        return toDto(connection);
    }

    /**
     * Retrieves a valid decrypted access token, automatically refreshing it if expired.
     */
    @Transactional
    public String getValidAccessToken(UUID connectionId) {
        GmailOAuthToken token = tokenRepository.findByConnectionId(connectionId)
                .orElseThrow(() -> new BusinessException("No Gmail tokens found for connection " + connectionId, HttpStatus.NOT_FOUND));

        Instant expiresAt = token.getTokenExpiresAt();
        if (expiresAt != null && Instant.now().isBefore(expiresAt)) {
            return encryptionService.decrypt(token.getEncryptedAccessToken());
        }

        // Token expired -> refresh using encrypted refresh token
        log.info("Gmail access token expired for connection {}. Refreshing token...", connectionId);
        String rawRefreshToken = encryptionService.decrypt(token.getEncryptedRefreshToken());
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException("Cannot refresh Gmail token: refresh token missing", HttpStatus.UNAUTHORIZED);
        }

        JsonNode refreshResponse = refreshAccessToken(rawRefreshToken);
        String newAccessToken = refreshResponse.path("access_token").asText();
        long expiresIn = refreshResponse.path("expires_in").asLong(3600);

        token.setEncryptedAccessToken(encryptionService.encrypt(newAccessToken));
        token.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn - 60));
        tokenRepository.save(token);

        return newAccessToken;
    }

    public GmailConnectionDto toDto(IntegrationConnection connection) {
        var activeWatch = watchRepository.findByConnectionIdAndActiveTrue(connection.getId());
        Instant watchExpiresAt = activeWatch.map(w -> w.getExpiresAt()).orElse(null);
        boolean watchActive = activeWatch.map(w -> w.isActive()).orElse(false);

        return new GmailConnectionDto(
                connection.getId(),
                connection.getUser().getId(),
                connection.getAccountIdentifier(),
                connection.getProvider(),
                connection.getStatus().name(),
                connection.getLastSyncedAt(),
                watchExpiresAt,
                watchActive
        );
    }

    private JsonNode exchangeCodeForTokens(String code) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("code", code);
            body.add("client_id", gmailProperties.getClientId());
            body.add("client_secret", gmailProperties.getClientSecret());
            body.add("redirect_uri", gmailProperties.getRedirectUri());
            body.add("grant_type", "authorization_code");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(GOOGLE_TOKEN_ENDPOINT, request, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Failed to exchange OAuth code for tokens: {}", e.getMessage());
            throw new BusinessException("OAuth code exchange failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private JsonNode refreshAccessToken(String refreshToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("refresh_token", refreshToken);
            body.add("client_id", gmailProperties.getClientId());
            body.add("client_secret", gmailProperties.getClientSecret());
            body.add("grant_type", "refresh_token");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(GOOGLE_TOKEN_ENDPOINT, request, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Failed to refresh Gmail token: {}", e.getMessage());
            throw new BusinessException("Token refresh failed: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
        }
    }

    private JsonNode fetchGoogleUserInfo(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(GOOGLE_USERINFO_ENDPOINT, HttpMethod.GET, request, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Failed to fetch Google userinfo: {}", e.getMessage());
            throw new BusinessException("Failed to verify Google account profile", HttpStatus.BAD_GATEWAY);
        }
    }

    private UUID extractUserIdFromState(String state) {
        if (state == null || !state.contains(":")) {
            throw new BusinessException("Invalid OAuth state parameter", HttpStatus.BAD_REQUEST);
        }
        String[] parts = state.split(":");
        try {
            return UUID.fromString(parts[parts.length - 1]);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Malformed user ID in OAuth state", HttpStatus.BAD_REQUEST);
        }
    }
}
