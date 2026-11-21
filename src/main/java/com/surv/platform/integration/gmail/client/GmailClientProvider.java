package com.surv.platform.integration.gmail.client;

import com.google.api.services.gmail.Gmail;
import com.surv.platform.domain.IntegrationConnection;

/**
 * Strategy interface providing a configured Google Gmail API client.
 * Allows effortless mocking in integration and unit tests without hitting live Google servers.
 */
public interface GmailClientProvider {
    Gmail getClient(IntegrationConnection connection);
    Gmail getClientForAccessToken(String accessToken);
}
