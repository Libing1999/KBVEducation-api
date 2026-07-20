-- ============================================================================
-- Email notifications: admin-configurable SMTP settings (single active row,
-- mirroring system_settings) + a per-message delivery log. smtp_password is
-- stored AES-GCM-encrypted by the application (SecretCipher) — never plain.
-- All SMTP columns are nullable: blank values fall back to the spring.mail.*
-- environment configuration, so env-only deployments keep working untouched.
-- ============================================================================

CREATE TABLE email_settings (
    id             UUID         PRIMARY KEY,
    smtp_host      VARCHAR(255),
    smtp_port      INTEGER,
    smtp_username  VARCHAR(255),
    smtp_password  TEXT,
    sender_name    VARCHAR(150),
    sender_email   VARCHAR(255),
    use_tls        BOOLEAN      NOT NULL DEFAULT TRUE,
    use_ssl        BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_email_settings_port CHECK (smtp_port IS NULL OR smtp_port BETWEEN 1 AND 65535)
);

CREATE UNIQUE INDEX uq_email_settings_active ON email_settings (active) WHERE active = TRUE AND is_deleted = FALSE;

-- Seed the single default row so the app never has a "no settings" state.
INSERT INTO email_settings (id) VALUES (gen_random_uuid());

-- ---------------------------------------------------------------------------
-- email_logs — one row per outbound message attempt (queued -> sent/failed;
-- skipped when no SMTP host is configured at all). Feeds ops visibility and
-- the audit requirement that failures are traceable.
-- ---------------------------------------------------------------------------
CREATE TABLE email_logs (
    id            UUID         PRIMARY KEY,
    recipient     VARCHAR(255) NOT NULL,
    subject       VARCHAR(255) NOT NULL,
    event_type    VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    error_message TEXT,
    student_id    UUID,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_email_logs_status CHECK (status IN ('QUEUED', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_email_logs_date ON email_logs (created_at DESC);
CREATE INDEX idx_email_logs_status_date ON email_logs (status, created_at DESC);
