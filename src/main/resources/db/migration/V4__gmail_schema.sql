-- ============================================================
-- Gmail OAuth tokens (encrypted at rest, never plaintext)
-- ============================================================
CREATE TABLE gmail_oauth_tokens (
    id                      UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    connection_id           UUID        NOT NULL REFERENCES integration_connections(id) ON DELETE CASCADE,
    google_user_id          VARCHAR(255) NOT NULL,
    google_email            VARCHAR(320) NOT NULL,
    encrypted_access_token  TEXT        NOT NULL,
    encrypted_refresh_token TEXT        NOT NULL,
    token_expires_at        TIMESTAMPTZ,
    granted_scopes          VARCHAR(2000) NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (connection_id)
);

CREATE INDEX idx_gmail_tokens_connection_id  ON gmail_oauth_tokens(connection_id);
CREATE INDEX idx_gmail_tokens_google_email   ON gmail_oauth_tokens(google_email);

-- ============================================================
-- Gmail push-notification watches
-- ============================================================
CREATE TABLE gmail_watches (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    connection_id   UUID        NOT NULL REFERENCES integration_connections(id) ON DELETE CASCADE,
    google_email    VARCHAR(320) NOT NULL,
    topic_name      VARCHAR(500) NOT NULL,
    history_id      BIGINT      NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    registered_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gmail_watches_connection_id ON gmail_watches(connection_id);
CREATE INDEX idx_gmail_watches_expires_at    ON gmail_watches(expires_at);
CREATE INDEX idx_gmail_watches_active        ON gmail_watches(active);

-- ============================================================
-- Gmail message deduplication registry (append-only)
-- ============================================================
CREATE TABLE gmail_message_records (
    id                 UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    connection_id      UUID        NOT NULL REFERENCES integration_connections(id) ON DELETE CASCADE,
    message_id         VARCHAR(255) NOT NULL,
    thread_id          VARCHAR(255),
    history_id         BIGINT,
    google_email       VARCHAR(320) NOT NULL,
    subject            VARCHAR(2000),
    sender             VARCHAR(500),
    received_at        TIMESTAMPTZ,
    has_attachments    BOOLEAN      NOT NULL DEFAULT FALSE,
    attachment_count   INTEGER      NOT NULL DEFAULT 0,
    processed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (connection_id, message_id)
);

CREATE INDEX idx_gmail_msg_records_connection_id ON gmail_message_records(connection_id);
CREATE INDEX idx_gmail_msg_records_message_id    ON gmail_message_records(message_id);
CREATE INDEX idx_gmail_msg_records_received_at   ON gmail_message_records(received_at);

-- ============================================================
-- Gmail attachment records (links Gmail attachments to document registry)
-- ============================================================
CREATE TABLE gmail_attachment_records (
    id                  UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    message_record_id   UUID        NOT NULL REFERENCES gmail_message_records(id) ON DELETE CASCADE,
    attachment_id       VARCHAR(500) NOT NULL,
    filename            VARCHAR(1000),
    declared_mime_type  VARCHAR(255),
    detected_mime_type  VARCHAR(255),
    file_size_bytes     BIGINT,
    sha256_hash         VARCHAR(64),
    document_id         UUID        REFERENCES documents(id),
    downloaded          BOOLEAN      NOT NULL DEFAULT FALSE,
    downloaded_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gmail_att_records_message_id    ON gmail_attachment_records(message_record_id);
CREATE INDEX idx_gmail_att_records_document_id   ON gmail_attachment_records(document_id);
CREATE INDEX idx_gmail_att_records_downloaded    ON gmail_attachment_records(downloaded);
