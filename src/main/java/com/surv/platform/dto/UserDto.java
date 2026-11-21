package com.surv.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.surv.platform.domain.User;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto {
    private UUID id;
    private String externalId;
    private String email;
    private String displayName;
    private String organization;
    private User.UserStatus status;
    private Instant lastSeenAt;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserDto from(User user) {
        return UserDto.builder()
                .id(user.getId())
                .externalId(user.getExternalId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .organization(user.getOrganization())
                .status(user.getStatus())
                .lastSeenAt(user.getLastSeenAt())
                .metadata(user.getMetadata())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
