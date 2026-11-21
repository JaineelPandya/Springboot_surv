package com.surv.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.surv.platform.domain.Document;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentDto {
    private UUID id;
    private UUID ownerId;
    private String ownerEmail;
    private String sourceDocumentId;
    private String title;
    private String mimeType;
    private Document.SensitivityLevel sensitivityLevel;
    private Document.DocumentStatus status;
    private Long fileSizeBytes;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;

    public static DocumentDto from(Document doc) {
        return DocumentDto.builder()
                .id(doc.getId())
                .ownerId(doc.getOwner().getId())
                .ownerEmail(doc.getOwner().getEmail())
                .sourceDocumentId(doc.getSourceDocumentId())
                .title(doc.getTitle())
                .mimeType(doc.getMimeType())
                .sensitivityLevel(doc.getSensitivityLevel())
                .status(doc.getStatus())
                .fileSizeBytes(doc.getFileSizeBytes())
                .metadata(doc.getMetadata())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
