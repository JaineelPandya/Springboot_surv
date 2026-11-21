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
@Table(name = "integration_connections", indexes = {
        @Index(name = "idx_integration_user_id", columnList = "user_id"),
        @Index(name = "idx_integration_provider", columnList = "provider"),
        @Index(name = "idx_integration_status", columnList = "status")
})
public class IntegrationConnection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider", nullable = false, length = 100)
    private String provider;

    @Column(name = "account_identifier", length = 500)
    private String accountIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ConnectionStatus status = ConnectionStatus.ACTIVE;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    public enum ConnectionStatus { ACTIVE, INACTIVE, ERROR, REVOKED }
}
