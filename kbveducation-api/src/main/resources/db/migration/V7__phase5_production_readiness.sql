-- ============================================================================
-- KBV Education — Phase 5 production-readiness schema
-- Certificates, generalized audit trail, system settings, backups, exports,
-- application error log. UUID PKs, full audit block + soft delete (mirrors
-- Phase 1-4). "Who"/"when" for every new table is BaseEntity's created_by/
-- created_at — no redundant domain columns duplicating that, same convention
-- score_audit_logs already established in V6.
-- Additive only — does not touch any existing table except the account-
-- lockout columns appended to users at the bottom.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- certificate_templates  (admin-managed; one active template per certificate_type)
-- ---------------------------------------------------------------------------
CREATE TABLE certificate_templates (
    id                         UUID         PRIMARY KEY,
    name                       VARCHAR(150) NOT NULL,
    certificate_type           VARCHAR(20)  NOT NULL,
    body_template              TEXT         NOT NULL,
    primary_color_hex          VARCHAR(7)   NOT NULL DEFAULT '#1B3A6B',
    institution_name_override  VARCHAR(150),
    logo_path_override         TEXT,
    active                     BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_cert_template_type CHECK (certificate_type IN ('TIER_1', 'TIER_2', 'TIER_3', 'COMPLETION'))
);

-- Only one active template per certificate type.
CREATE UNIQUE INDEX uq_cert_template_active_type
    ON certificate_templates (certificate_type) WHERE active = TRUE AND is_deleted = FALSE;

-- ---------------------------------------------------------------------------
-- certificates  (append-only issuance record; the PDF is rendered once at
-- generate time and never re-rendered on download — branding is snapshotted
-- onto this row rather than read live from certificate_templates/
-- system_settings, so later template/settings edits never retroactively
-- change a certificate a student already received)
-- ---------------------------------------------------------------------------
CREATE TABLE certificates (
    id                          UUID         PRIMARY KEY,
    student_id                  UUID         NOT NULL,
    template_id                 UUID         NOT NULL,
    certificate_type            VARCHAR(20)  NOT NULL,
    certificate_number          VARCHAR(40)  NOT NULL,
    cohort_id                   UUID,
    tier_at_issue                VARCHAR(30),
    file_path                   TEXT         NOT NULL,
    institution_name_snapshot   VARCHAR(150) NOT NULL,
    logo_path_snapshot          TEXT,
    primary_color_snapshot      VARCHAR(7)   NOT NULL,
    status                       VARCHAR(20)  NOT NULL DEFAULT 'ISSUED',

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_certificates_student  FOREIGN KEY (student_id)  REFERENCES users (id),
    CONSTRAINT fk_certificates_template FOREIGN KEY (template_id) REFERENCES certificate_templates (id),
    CONSTRAINT fk_certificates_cohort   FOREIGN KEY (cohort_id)   REFERENCES cohorts (id),
    CONSTRAINT ck_certificates_type     CHECK (certificate_type IN ('TIER_1', 'TIER_2', 'TIER_3', 'COMPLETION')),
    CONSTRAINT ck_certificates_status   CHECK (status IN ('ISSUED'))
);

CREATE UNIQUE INDEX uq_certificates_number ON certificates (certificate_number) WHERE is_deleted = FALSE;
CREATE INDEX idx_certificates_student ON certificates (student_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- audit_logs  (general-purpose cross-cutting audit trail — separate from the
-- Phase 4 score_audit_logs, which stays scoped to score/tier domain events
-- and is untouched here. action/entity_type are intentionally NOT CHECK-
-- constrained, same as score_audit_logs, since coverage grows incrementally
-- across later steps; validated at the application layer instead.)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id                    UUID        PRIMARY KEY,
    actor_email_snapshot  VARCHAR(255),
    action                VARCHAR(50) NOT NULL,
    entity_type           VARCHAR(50) NOT NULL,
    entity_id             UUID,
    old_value             TEXT,
    new_value             TEXT,
    ip_address            VARCHAR(64),
    user_agent            VARCHAR(512),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_audit_logs_actor_date ON audit_logs (created_by, created_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_action_date ON audit_logs (action, created_at DESC);

-- ---------------------------------------------------------------------------
-- system_settings  (single active row; admin-editable branding/locale/limits/
-- security/feature toggles — mirrors the score_config single-active-row
-- pattern exactly. leaderboard_enabled is NOT duplicated here — it already
-- lives on score_config from Phase 4.)
-- ---------------------------------------------------------------------------
CREATE TABLE system_settings (
    id                          UUID         PRIMARY KEY,
    application_name            VARCHAR(150) NOT NULL DEFAULT 'KBV Education',
    institution_name            VARCHAR(150) NOT NULL DEFAULT 'KBV Education',
    logo_path                   TEXT,
    primary_color_hex           VARCHAR(7)   NOT NULL DEFAULT '#1B3A6B',
    secondary_color_hex         VARCHAR(7)   NOT NULL DEFAULT '#F2F6FA',
    accent_color_hex            VARCHAR(7)   NOT NULL DEFAULT '#C4972A',
    timezone                    VARCHAR(50)  NOT NULL DEFAULT 'UTC',
    date_format                 VARCHAR(20)  NOT NULL DEFAULT 'yyyy-MM-dd',
    max_file_size_mb            INTEGER      NOT NULL DEFAULT 25,
    allowed_file_types          VARCHAR(255) NOT NULL DEFAULT 'pdf,doc,docx,jpg,jpeg,png,mp3,m4a,webm,mp4',
    max_login_attempts          INTEGER      NOT NULL DEFAULT 5,
    password_min_length         INTEGER      NOT NULL DEFAULT 8,
    password_require_uppercase  BOOLEAN      NOT NULL DEFAULT FALSE,
    password_require_lowercase  BOOLEAN      NOT NULL DEFAULT FALSE,
    password_require_digit      BOOLEAN      NOT NULL DEFAULT FALSE,
    password_require_special    BOOLEAN      NOT NULL DEFAULT FALSE,
    session_timeout_minutes     INTEGER      NOT NULL DEFAULT 10080,
    maintenance_mode            BOOLEAN      NOT NULL DEFAULT FALSE,
    certificate_enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    export_enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    active                      BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_system_settings_login_attempts  CHECK (max_login_attempts BETWEEN 1 AND 20),
    CONSTRAINT ck_system_settings_password_len    CHECK (password_min_length BETWEEN 6 AND 128),
    CONSTRAINT ck_system_settings_session_timeout CHECK (session_timeout_minutes BETWEEN 5 AND 43200),
    CONSTRAINT ck_system_settings_max_file_size   CHECK (max_file_size_mb BETWEEN 1 AND 500)
);

-- Only one active settings row at a time.
CREATE UNIQUE INDEX uq_system_settings_active ON system_settings (active) WHERE active = TRUE AND is_deleted = FALSE;

-- Seed the single default settings row so the app never has a "no settings" state.
INSERT INTO system_settings (id) VALUES (gen_random_uuid());

-- ---------------------------------------------------------------------------
-- backup_history  (manual, admin-triggered database dumps — no scheduling)
-- ---------------------------------------------------------------------------
CREATE TABLE backup_history (
    id              UUID        PRIMARY KEY,
    file_path       TEXT,
    file_size_bytes BIGINT,
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    error_message   TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_backup_history_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_backup_history_date ON backup_history (created_at DESC);

-- ---------------------------------------------------------------------------
-- export_history  (records every export run, old Phase 4 datasets and new
-- Phase 5 ones alike, for the "Today's Exports" dashboard card and audit trail)
-- ---------------------------------------------------------------------------
CREATE TABLE export_history (
    id               UUID        PRIMARY KEY,
    dataset          VARCHAR(30) NOT NULL,
    format           VARCHAR(10) NOT NULL,
    filters_snapshot TEXT,
    row_count        INTEGER,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_export_history_format  CHECK (format IN ('CSV', 'XLSX')),
    CONSTRAINT ck_export_history_dataset CHECK (dataset IN (
        'LEADERBOARD', 'COMPOSITE_SCORES', 'STUDENTS', 'PARENTS', 'COHORTS', 'LESSONS',
        'HOMEWORK', 'QUIZZES', 'REFLECTIONS', 'PRACTICE_LOGS', 'ANALYTICS', 'AUDIT_LOGS'))
);

CREATE INDEX idx_export_history_date ON export_history (created_at DESC);

-- ---------------------------------------------------------------------------
-- application_logs  (unhandled exceptions, auth/authz failures, upload/export
-- errors — hooked from the existing GlobalExceptionHandler. severity is
-- CHECK-constrained since it's a small fixed set that drives the admin
-- viewer's default filter, unlike audit_logs' still-growing action list.)
-- ---------------------------------------------------------------------------
CREATE TABLE application_logs (
    id                   UUID        PRIMARY KEY,
    severity             VARCHAR(10) NOT NULL,
    source               VARCHAR(150) NOT NULL,
    message              TEXT        NOT NULL,
    stack_trace_excerpt  TEXT,
    endpoint             VARCHAR(255),
    http_method          VARCHAR(10),
    ip_address           VARCHAR(64),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_application_logs_severity CHECK (severity IN ('ERROR', 'WARNING'))
);

CREATE INDEX idx_application_logs_date ON application_logs (created_at DESC);
CREATE INDEX idx_application_logs_severity_date ON application_logs (severity, created_at DESC);

-- ---------------------------------------------------------------------------
-- users — additive account-lockout columns.
-- ---------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMPTZ;
