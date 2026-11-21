package com.surv.platform.domain;

import com.surv.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_documents_source_doc_id", columnList = "source_document_id"),
        @Index(name = "idx_documents_owner_id", columnList = "owner_id"),
        @Index(name = "idx_documents_status", columnList = "status"),
        @Index(name = "idx_documents_sensitivity", columnList = "sensitivity_level")
})
public class Document extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "source_document_id", nullable = false, length = 500)
    private String sourceDocumentId;

    @Column(name = "title", nullable = false, length = 1000)
    private String title;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensitivity_level", nullable = false, length = 50)
    private SensitivityLevel sensitivityLevel = SensitivityLevel.INTERNAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DocumentStatus status = DocumentStatus.ACTIVE;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentVersion> versions = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentRecipient> recipients = new ArrayList<>();

    public enum SensitivityLevel { PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED, TOP_SECRET }
    public enum DocumentStatus { ACTIVE, ARCHIVED, DELETED, QUARANTINED }
}
