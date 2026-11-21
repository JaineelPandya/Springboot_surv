package com.surv.platform.domain;

import com.surv.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "risk_rules", indexes = {
        @Index(name = "idx_risk_rules_name", columnList = "name", unique = true),
        @Index(name = "idx_risk_rules_enabled", columnList = "enabled"),
        @Index(name = "idx_risk_rules_severity", columnList = "severity")
})
public class RiskRule extends BaseEntity {

    @Column(name = "name", nullable = false, length = 255, unique = true)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 50)
    private Severity severity;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", columnDefinition = "jsonb")
    private Map<String, Object> conditions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actions", columnDefinition = "jsonb")
    private Map<String, Object> actions;

    @Column(name = "priority")
    private Integer priority = 100;

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
}
