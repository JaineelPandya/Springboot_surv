package com.surv.platform.domain;

import com.surv.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Tracks a Gmail push-notification watch registration.
 * Watches expire every 7 days and must be renewed proactively.
 */
@Getter
@Setter
@Entity
@Table(name = "gmail_watches")
public class GmailWatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private IntegrationConnection connection;

    @Column(name = "google_email", nullable = false, length = 320)
    private String googleEmail;

    @Column(name = "topic_name", nullable = false, length = 500)
    private String topicName;

    /** historyId returned by gmail.users.watch() — used as the sync anchor */
    @Column(name = "history_id", nullable = false)
    private Long historyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;
}
