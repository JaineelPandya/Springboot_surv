package com.surv.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.surv.platform.domain.DocumentEvent;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentEventDto {
    private UUID id;
    private UUID documentId;
    private UUID actorId;
    private String sourceEventId;
    private String eventType;
    private Instant occurredAt;
    private Instant ingestedAt;
    private String actorEmail;
    private String actorIp;
    private Map<String, Object> metadata;
    private Instant createdAt;

    public static DocumentEventDto from(DocumentEvent event) {
        return DocumentEventDto.builder()
                .id(event.getId())
                .documentId(event.getDocument().getId())
                .actorId(event.getActor() != null ? event.getActor().getId() : null)
                .sourceEventId(event.getSourceEventId())
                .eventType(event.getEventType())
                .occurredAt(event.getOccurredAt())
                .ingestedAt(event.getIngestedAt())
                .actorEmail(event.getActorEmail())
                .actorIp(event.getActorIp())
                .metadata(event.getMetadata())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
