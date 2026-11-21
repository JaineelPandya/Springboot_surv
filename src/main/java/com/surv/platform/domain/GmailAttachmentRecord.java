package com.surv.platform.domain;

import com.surv.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Records an email attachment identified in a Gmail message.
 * Links the Gmail attachment to a Document Registry entry when downloaded.
 * Downloads only happen on explicit authorization; metadata is recorded regardless.
 */
@Getter
@Setter
@Entity
@Table(name = "gmail_attachment_records")
public class GmailAttachmentRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_record_id", nullable = false)
    private GmailMessageRecord messageRecord;

    /** Gmail's internal attachment part ID within the message */
    @Column(name = "attachment_id", nullable = false, length = 500)
    private String attachmentId;

    @Column(name = "filename", length = 1000)
    private String filename;

    /** MIME type as declared in the email (may be inaccurate — Tika used on download) */
    @Column(name = "declared_mime_type", length = 255)
    private String declaredMimeType;

    /** MIME type as detected by Tika on actual content (set only after download) */
    @Column(name = "detected_mime_type", length = 255)
    private String detectedMimeType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** SHA-256 of actual attachment bytes — only set after authorized download */
    @Column(name = "sha256_hash", length = 64)
    private String sha256Hash;

    /** Document Registry entry created for this attachment after download */
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "downloaded", nullable = false)
    private boolean downloaded = false;

    @Column(name = "downloaded_at")
    private Instant downloadedAt;
}
