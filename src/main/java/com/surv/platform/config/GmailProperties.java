package com.surv.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.google.gmail")
public class GmailProperties {
    private String clientId = "sample-google-client-id";
    private String clientSecret = "sample-google-client-secret";
    private String redirectUri = "http://localhost:8080/api/v1/integrations/gmail/oauth2/callback";
    private String pubSubTopicName = "projects/my-gcp-project/topics/gmail-notifications";
    private String encryptionKey = "0123456789abcdef0123456789abcdef"; // 256-bit AES key in hex or string
    private List<String> scopes = List.of(
            "https://www.googleapis.com/auth/gmail.readonly",
            "https://www.googleapis.com/auth/userinfo.email"
    );
    private boolean autoRenewWatches = true;
    private long watchRenewalThresholdHours = 24; // Renew if expires within 24h
    private boolean periodicReconciliationEnabled = true;
}
