package com.surv.platform.domain;

import com.surv.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "document_recipients", indexes = {
        @Index(name = "idx_doc_recipients_document_id", columnList = "document_id"),
        @Index(name = "idx_doc_recipients_user_id", columnList = "user_id"),
        @Index(name = "idx_doc_recipients_unique", columnList = "document_id, user_id", unique = true)
})
public class DocumentRecipient extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 50)
    private AccessLevel accessLevel;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "granted_by", length = 255)
    private String grantedBy;

    public enum AccessLevel { VIEW, COMMENT, EDIT, DOWNLOAD, FULL }
}
