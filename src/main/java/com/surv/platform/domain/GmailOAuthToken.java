package com.surv.platform.domain;

import com.surv.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Stores Google OAuth2 tokens for a Gmail connection.
 * Access and refresh tokens are AES-256-GCM encrypted at rest.
 * NEVER store raw token values in this entity — always use CredentialEncryptionService.
 */
@Getter
@Setter
@Entity
@Table(name = "gmail_oauth_tokens")
public class GmailOAuthToken extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false, unique = true)
    private IntegrationConnection connection;

    @Column(name = "google_user_id", nullable = false, length = 255)
    private String googleUserId;

    @Column(name = "google_email", nullable = false, length = 320)
    private String googleEmail;

    /** AES-256-GCM encrypted. Format: Base64(IV||ciphertext||tag) */
    @Column(name = "encrypted_access_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedAccessToken;

    /** AES-256-GCM encrypted. Format: Base64(IV||ciphertext||tag) */
    @Column(name = "encrypted_refresh_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "granted_scopes", nullable = false, length = 2000)
    private String grantedScopes;
}
