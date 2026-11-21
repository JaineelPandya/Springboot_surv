package com.surv.platform.domain;

import com.surv.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "security_alerts", indexes = {
        @Index(name = "idx_security_alerts_document_id", columnList = "document_id"),
        @Index(name = "idx_security_alerts_status", columnList = "status"),
        @Index(name = "idx_security_alerts_severity", columnList = "severity"),
        @Index(name = "idx_security_alerts_triggered_at", columnList = "triggered_at")
})
public class SecurityAlert extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "risk_rule_id")
    private RiskRule riskRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_event_id")
    private DocumentEvent documentEvent;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 50)
    private RiskRule.Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_data", columnDefinition = "jsonb")
    private Map<String, Object> contextData;

    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlertInvestigation> investigations = new ArrayList<>();

    public enum AlertStatus { OPEN, INVESTIGATING, RESOLVED, FALSE_POSITIVE, ESCALATED }
}
