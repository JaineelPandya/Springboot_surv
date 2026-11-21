package com.surv.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.surv.platform.domain.RiskRule;
import com.surv.platform.domain.SecurityAlert;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityAlertDto {
    private UUID id;
    private UUID documentId;
    private UUID riskRuleId;
    private String title;
    private String description;
    private RiskRule.Severity severity;
    private SecurityAlert.AlertStatus status;
    private Instant triggeredAt;
    private Map<String, Object> contextData;
    private Instant createdAt;
    private Instant updatedAt;

    public static SecurityAlertDto from(SecurityAlert alert) {
        return SecurityAlertDto.builder()
                .id(alert.getId())
                .documentId(alert.getDocument() != null ? alert.getDocument().getId() : null)
                .riskRuleId(alert.getRiskRule() != null ? alert.getRiskRule().getId() : null)
                .title(alert.getTitle())
                .description(alert.getDescription())
                .severity(alert.getSeverity())
                .status(alert.getStatus())
                .triggeredAt(alert.getTriggeredAt())
                .contextData(alert.getContextData())
                .createdAt(alert.getCreatedAt())
                .updatedAt(alert.getUpdatedAt())
                .build();
    }
}
