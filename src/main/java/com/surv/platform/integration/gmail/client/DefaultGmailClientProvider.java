package com.surv.platform.integration.gmail.client;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.surv.platform.domain.IntegrationConnection;
import com.surv.platform.integration.gmail.service.GmailOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DefaultGmailClientProvider implements GmailClientProvider {

    private final GmailOAuthService oAuthService;

    @Override
    public Gmail getClient(IntegrationConnection connection) {
        String validAccessToken = oAuthService.getValidAccessToken(connection.getId());
        return getClientForAccessToken(validAccessToken);
    }

    @Override
    public Gmail getClientForAccessToken(String accessToken) {
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

            // 1-hour default expiry for runtime token representation
            AccessToken token = new AccessToken(accessToken, new Date(System.currentTimeMillis() + 3600000));
            GoogleCredentials credentials = GoogleCredentials.create(token);
            HttpCredentialsAdapter requestInitializer = new HttpCredentialsAdapter(credentials);

            return new Gmail.Builder(httpTransport, jsonFactory, requestInitializer)
                    .setApplicationName("SurveillancePlatform")
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            log.error("Failed to initialize Gmail API client: {}", e.getMessage());
            throw new IllegalStateException("Failed to initialize Gmail API client", e);
        }
    }
}
