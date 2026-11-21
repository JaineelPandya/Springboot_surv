package com.surv.platform.domain;

import com.surv.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "alert_investigations", indexes = {
        @Index(name = "idx_alert_investigations_alert_id", columnList = "alert_id"),
        @Index(name = "idx_alert_investigations_investigator_id", columnList = "investigator_id")
})
public class AlertInvestigation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    private SecurityAlert alert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigator_id")
    private User investigator;

    @Column(name = "investigator_email", length = 320)
    private String investigatorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 100)
    private InvestigationAction action;

    @Column(name = "notes", length = 4000)
    private String notes;

    @Column(name = "action_at", nullable = false)
    private Instant actionAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 50)
    private SecurityAlert.AlertStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 50)
    private SecurityAlert.AlertStatus newStatus;

    public enum InvestigationAction { OPENED, ASSIGNED, NOTE_ADDED, STATUS_CHANGED, ESCALATED, RESOLVED, CLOSED }
}
