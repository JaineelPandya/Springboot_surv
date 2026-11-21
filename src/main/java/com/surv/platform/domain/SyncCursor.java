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
@Table(name = "sync_cursors", indexes = {
        @Index(name = "idx_sync_cursors_connection_id", columnList = "connection_id"),
        @Index(name = "idx_sync_cursors_unique", columnList = "connection_id, cursor_type", unique = true)
})
public class SyncCursor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private IntegrationConnection connection;

    @Column(name = "cursor_type", nullable = false, length = 100)
    private String cursorType;

    @Column(name = "cursor_value", length = 2000)
    private String cursorValue;

    @Column(name = "last_processed_at")
    private Instant lastProcessedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state", columnDefinition = "jsonb")
    private Map<String, Object> state;
}
