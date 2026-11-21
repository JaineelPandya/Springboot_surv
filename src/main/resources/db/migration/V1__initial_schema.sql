-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id            UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    external_id   VARCHAR(255),
    email         VARCHAR(320) NOT NULL,
    display_name  VARCHAR(255),
    organization  VARCHAR(255),
    status        VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    last_seen_at  TIMESTAMPTZ,
    metadata      JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_external_id ON users(external_id);

-- ============================================================
-- DOCUMENTS
-- ============================================================
CREATE TABLE documents (
    id                  UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    owner_id            UUID        NOT NULL REFERENCES users(id),
    source_document_id  VARCHAR(500) NOT NULL,
    title               VARCHAR(1000) NOT NULL,
    mime_type           VARCHAR(255),
    sensitivity_level   VARCHAR(50)  NOT NULL DEFAULT 'INTERNAL',
    status              VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    file_size_bytes     BIGINT,
    metadata            JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_source_doc_id ON documents(source_document_id);
CREATE INDEX idx_documents_owner_id ON documents(owner_id);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_sensitivity ON documents(sensitivity_level);

-- ============================================================
-- DOCUMENT VERSIONS
-- ============================================================
CREATE TABLE document_versions (
    id                  UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    document_id         UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_number      INTEGER     NOT NULL,
    source_revision_id  VARCHAR(500),
    file_size_bytes     BIGINT,
    checksum            VARCHAR(128),
    change_summary      VARCHAR(2000),
    metadata            JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, version_number)
);

CREATE INDEX idx_doc_versions_document_id ON document_versions(document_id);
CREATE INDEX idx_doc_versions_version_number ON document_versions(document_id, version_number);

-- ============================================================
-- DOCUMENT RECIPIENTS
-- ============================================================
CREATE TABLE document_recipients (
    id           UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    document_id  UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users(id),
    access_level VARCHAR(50) NOT NULL,
    granted_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ,
    granted_by   VARCHAR(255),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, user_id)
);

CREATE INDEX idx_doc_recipients_document_id ON document_recipients(document_id);
CREATE INDEX idx_doc_recipients_user_id ON document_recipients(user_id);
CREATE UNIQUE INDEX idx_doc_recipients_unique ON document_recipients(document_id, user_id);

-- ============================================================
-- INTEGRATION CONNECTIONS
-- ============================================================
CREATE TABLE integration_connections (
    id                  UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id             UUID        NOT NULL REFERENCES users(id),
    provider            VARCHAR(100) NOT NULL,
    account_identifier  VARCHAR(500),
    status              VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    last_synced_at      TIMESTAMPTZ,
    error_message       VARCHAR(2000),
    config              JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_integration_user_id ON integration_connections(user_id);
CREATE INDEX idx_integration_provider ON integration_connections(provider);
CREATE INDEX idx_integration_status ON integration_connections(status);

-- ============================================================
-- SYNC CURSORS
-- ============================================================
CREATE TABLE sync_cursors (
    id                   UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    connection_id        UUID        NOT NULL REFERENCES integration_connections(id) ON DELETE CASCADE,
    cursor_type          VARCHAR(100) NOT NULL,
    cursor_value         VARCHAR(2000),
    last_processed_at    TIMESTAMPTZ,
    state                JSONB,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (connection_id, cursor_type)
);

CREATE INDEX idx_sync_cursors_connection_id ON sync_cursors(connection_id);
CREATE UNIQUE INDEX idx_sync_cursors_unique ON sync_cursors(connection_id, cursor_type);

-- ============================================================
-- DOCUMENT EVENTS (append-only)
-- ============================================================
CREATE TABLE document_events (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    document_id     UUID        NOT NULL REFERENCES documents(id),
    actor_id        UUID        REFERENCES users(id),
    source_event_id VARCHAR(500) NOT NULL UNIQUE,
    event_type      VARCHAR(100) NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    ingested_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    actor_email     VARCHAR(320),
    actor_ip        VARCHAR(45),
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_doc_events_document_id ON document_events(document_id);
CREATE INDEX idx_doc_events_actor_id ON document_events(actor_id);
CREATE INDEX idx_doc_events_event_type ON document_events(event_type);
CREATE INDEX idx_doc_events_occurred_at ON document_events(occurred_at);
CREATE UNIQUE INDEX idx_doc_events_source_event_id ON document_events(source_event_id);

-- ============================================================
-- VIEWING SESSIONS
-- ============================================================
CREATE TABLE viewing_sessions (
    id               UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    document_id      UUID        NOT NULL REFERENCES documents(id),
    viewer_id        UUID        REFERENCES users(id),
    viewer_email     VARCHAR(320),
    started_at       TIMESTAMPTZ NOT NULL,
    ended_at         TIMESTAMPTZ,
    duration_seconds BIGINT,
    viewer_ip        VARCHAR(45),
    user_agent       VARCHAR(1000),
    metadata         JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_viewing_sessions_document_id ON viewing_sessions(document_id);
CREATE INDEX idx_viewing_sessions_viewer_id ON viewing_sessions(viewer_id);
CREATE INDEX idx_viewing_sessions_started_at ON viewing_sessions(started_at);

-- ============================================================
-- RISK RULES
-- ============================================================
CREATE TABLE risk_rules (
    id          UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(2000),
    severity    VARCHAR(50)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    conditions  JSONB,
    actions     JSONB,
    priority    INTEGER      NOT NULL DEFAULT 100,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_risk_rules_name ON risk_rules(name);
CREATE INDEX idx_risk_rules_enabled ON risk_rules(enabled);
CREATE INDEX idx_risk_rules_severity ON risk_rules(severity);

-- ============================================================
-- SECURITY ALERTS
-- ============================================================
CREATE TABLE security_alerts (
    id                UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    document_id       UUID        REFERENCES documents(id),
    risk_rule_id      UUID        REFERENCES risk_rules(id),
    document_event_id UUID        REFERENCES document_events(id),
    title             VARCHAR(500) NOT NULL,
    description       VARCHAR(4000),
    severity          VARCHAR(50)  NOT NULL,
    status            VARCHAR(50)  NOT NULL DEFAULT 'OPEN',
    triggered_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    context_data      JSONB,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_alerts_document_id ON security_alerts(document_id);
CREATE INDEX idx_security_alerts_status ON security_alerts(status);
CREATE INDEX idx_security_alerts_severity ON security_alerts(severity);
CREATE INDEX idx_security_alerts_triggered_at ON security_alerts(triggered_at);

-- ============================================================
-- ALERT INVESTIGATIONS
-- ============================================================
CREATE TABLE alert_investigations (
    id               UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    alert_id         UUID        NOT NULL REFERENCES security_alerts(id) ON DELETE CASCADE,
    investigator_id  UUID        REFERENCES users(id),
    investigator_email VARCHAR(320),
    action           VARCHAR(100) NOT NULL,
    notes            VARCHAR(4000),
    action_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    previous_status  VARCHAR(50),
    new_status       VARCHAR(50),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_investigations_alert_id ON alert_investigations(alert_id);
CREATE INDEX idx_alert_investigations_investigator_id ON alert_investigations(investigator_id);

-- ============================================================
-- AUDIT LOGS (append-only)
-- ============================================================
CREATE TABLE audit_logs (
    id           UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    VARCHAR(36),
    actor_id     VARCHAR(36),
    actor_email  VARCHAR(320),
    action       VARCHAR(200) NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_address   VARCHAR(45),
    user_agent   VARCHAR(1000),
    changes      JSONB,
    request_id   VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_entity_type_id ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor_id ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_occurred_at ON audit_logs(occurred_at);
