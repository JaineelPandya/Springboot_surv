package com.surv.platform.auth;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresInSeconds,
    String role
) {}
