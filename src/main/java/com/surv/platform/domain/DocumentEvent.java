package com.surv.platform.domain;

import com.surv.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "document_events", indexes = {
        @Index(name = "idx_doc_events_document_id", columnList = "document_id"),
        @Index(name = "idx_doc_events_actor_id", columnList = "actor_id"),
        @Index(name = "idx_doc_events_event_type", columnList = "event_type"),
        @Index(name = "idx_doc_events_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_doc_events_source_event_id", columnList = "source_event_id", unique = true)
})
public class DocumentEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "source_event_id", nullable = false, length = 500, unique = true)
    private String sourceEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Column(name = "actor_email", length = 320)
    private String actorEmail;

    @Column(name = "actor_ip", length = 45)
    private String actorIp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
