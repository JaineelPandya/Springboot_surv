package com.surv.platform.domain;

import com.surv.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Idempotency record for processed Gmail messages.
 * Prevents duplicate processing when Pub/Sub delivers the same notification multiple times.
 */
@Getter
@Setter
@Entity
@Table(name = "gmail_message_records")
public class GmailMessageRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private IntegrationConnection connection;

    @Column(name = "message_id", nullable = false, length = 255)
    private String messageId;

    @Column(name = "thread_id", length = 255)
    private String threadId;

    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "google_email", nullable = false, length = 320)
    private String googleEmail;

    @Column(name = "subject", length = 2000)
    private String subject;

    @Column(name = "sender", length = 500)
    private String sender;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "has_attachments", nullable = false)
    private boolean hasAttachments = false;

    @Column(name = "attachment_count", nullable = false)
    private int attachmentCount = 0;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
