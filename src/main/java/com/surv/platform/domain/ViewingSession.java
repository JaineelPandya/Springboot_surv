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
@Table(name = "viewing_sessions", indexes = {
        @Index(name = "idx_viewing_sessions_document_id", columnList = "document_id"),
        @Index(name = "idx_viewing_sessions_viewer_id", columnList = "viewer_id"),
        @Index(name = "idx_viewing_sessions_started_at", columnList = "started_at")
})
public class ViewingSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viewer_id")
    private User viewer;

    @Column(name = "viewer_email", length = 320)
    private String viewerEmail;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "viewer_ip", length = 45)
    private String viewerIp;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
