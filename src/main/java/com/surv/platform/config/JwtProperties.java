package com.surv.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret = "CHANGE_ME_IN_PRODUCTION_USE_AT_LEAST_32_CHARS_SECRET_KEY_HERE";
    private long accessTokenExpirationMs = 900_000L;      // 15 minutes
    private long refreshTokenExpirationMs = 604_800_000L; // 7 days
    private String issuer = "surveillance-platform";
}
