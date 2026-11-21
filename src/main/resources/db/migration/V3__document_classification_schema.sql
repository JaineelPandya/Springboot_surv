-- Classification rules for configurable keyword/metadata-based classification
CREATE TABLE classification_rules (
    id                  UUID         NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    name                VARCHAR(255) NOT NULL UNIQUE,
    description         VARCHAR(2000),
    target_level        VARCHAR(50)  NOT NULL,
    keywords            JSONB,
    metadata_matchers   JSONB,
    mime_type_patterns  JSONB,
    priority            INTEGER      NOT NULL DEFAULT 100,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_classification_rules_enabled      ON classification_rules(enabled);
CREATE INDEX idx_classification_rules_target_level ON classification_rules(target_level);
CREATE UNIQUE INDEX idx_classification_rules_name  ON classification_rules(name);

-- Document fingerprints for integrity verification
CREATE TABLE document_fingerprints (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    document_id     UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_id      UUID        REFERENCES document_versions(id) ON DELETE CASCADE,
    sha256_hash     VARCHAR(64) NOT NULL,
    file_size_bytes BIGINT      NOT NULL,
    detected_mime   VARCHAR(255),
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_doc_fingerprints_document_id ON document_fingerprints(document_id);
CREATE INDEX idx_doc_fingerprints_sha256       ON document_fingerprints(sha256_hash);

-- Document classification change history (append-only audit)
CREATE TABLE classification_history (
    id                    UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    document_id           UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    previous_level        VARCHAR(50),
    new_level             VARCHAR(50) NOT NULL,
    classification_method VARCHAR(50) NOT NULL,
    changed_by            UUID        REFERENCES users(id),
    changed_by_username   VARCHAR(100),
    rule_id               UUID        REFERENCES classification_rules(id),
    reason                VARCHAR(1000),
    changed_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_class_history_document_id ON classification_history(document_id);
CREATE INDEX idx_class_history_changed_at  ON classification_history(changed_at);
