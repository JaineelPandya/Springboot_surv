-- Application accounts (separate from Google Workspace auth)
CREATE TABLE app_accounts (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    username        VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL DEFAULT 'EMPLOYEE',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    locked          BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    last_login_ip   VARCHAR(45),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_app_accounts_username ON app_accounts(username);
CREATE INDEX idx_app_accounts_user_id ON app_accounts(user_id);
CREATE INDEX idx_app_accounts_role ON app_accounts(role);

-- Refresh tokens (stored server-side for revocation)
CREATE TABLE refresh_tokens (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    account_id      UUID        NOT NULL REFERENCES app_accounts(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    revoked_reason  VARCHAR(255),
    issuer_ip       VARCHAR(45),
    user_agent      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_account_id ON refresh_tokens(account_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- Authorization audit log
CREATE TABLE auth_audit_logs (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    account_id      UUID        REFERENCES app_accounts(id),
    username        VARCHAR(100),
    action          VARCHAR(100) NOT NULL,
    outcome         VARCHAR(50)  NOT NULL,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    details         JSONB,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auth_audit_logs_account_id ON auth_audit_logs(account_id);
CREATE INDEX idx_auth_audit_logs_action ON auth_audit_logs(action);
CREATE INDEX idx_auth_audit_logs_occurred_at ON auth_audit_logs(occurred_at);
