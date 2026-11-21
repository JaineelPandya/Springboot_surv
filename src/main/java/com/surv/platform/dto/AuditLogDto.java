package com.surv.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.surv.platform.domain.AuditLog;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogDto {
    private UUID id;
    private String entityType;
    private String entityId;
    private String actorId;
    private String actorEmail;
    private String action;
    private Instant occurredAt;
    private String ipAddress;
    private Map<String, Object> changes;
    private Instant createdAt;

    public static AuditLogDto from(AuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .actorId(log.getActorId())
                .actorEmail(log.getActorEmail())
                .action(log.getAction())
                .occurredAt(log.getOccurredAt())
                .ipAddress(log.getIpAddress())
                .changes(log.getChanges())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
