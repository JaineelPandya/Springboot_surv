package com.surv.platform.domain;

import com.surv.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "classification_rules")
public class ClassificationRule extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_level", nullable = false, length = 50)
    private Document.SensitivityLevel targetLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keywords", columnDefinition = "jsonb")
    private List<String> keywords;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_matchers", columnDefinition = "jsonb")
    private Map<String, Object> metadataMatchers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mime_type_patterns", columnDefinition = "jsonb")
    private List<String> mimeTypePatterns;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
