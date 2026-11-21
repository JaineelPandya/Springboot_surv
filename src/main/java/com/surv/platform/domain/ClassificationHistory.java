package com.surv.platform.domain;

import com.surv.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "classification_history")
public class ClassificationHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_level", length = 50)
    private Document.SensitivityLevel previousLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_level", nullable = false, length = 50)
    private Document.SensitivityLevel newLevel;

    @Column(name = "classification_method", nullable = false, length = 50)
    private String classificationMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(name = "changed_by_username", length = 100)
    private String changedByUsername;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private ClassificationRule rule;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}
