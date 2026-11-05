-- ============================================================================
-- AI engine (memos "AI for the admin side" 12.10.2026 and "AI plan" 17.10.2026)
--
-- Claude is the primary AI, Gemini the backup and the voice-note transcriber.
-- Everything the AI produces is admin-only and stored separately from the
-- student's original submission, which is never overwritten.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- ai_settings  (single active row, same pattern as system_settings)
-- ---------------------------------------------------------------------------
CREATE TABLE ai_settings (
    id                             UUID         PRIMARY KEY,
    ai_enabled                     BOOLEAN      NOT NULL DEFAULT FALSE,
    practice_provider              VARCHAR(20)  NOT NULL DEFAULT 'CLAUDE',
    reflection_provider            VARCHAR(20)  NOT NULL DEFAULT 'CLAUDE',
    claude_model                   VARCHAR(100) NOT NULL DEFAULT 'claude-sonnet-5-5',
    gemini_model                   VARCHAR(100) NOT NULL DEFAULT 'gemini-3.8-flash',
    gemini_transcribe_model        VARCHAR(100) NOT NULL DEFAULT 'gemini-3.5-transcribe',
    claude_effort                  VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    call_mode                      VARCHAR(10)  NOT NULL DEFAULT 'BATCH',
    backup_enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    nightly_run_time               TIME         NOT NULL DEFAULT '23:00',
    run_timezone                   VARCHAR(50)  NOT NULL DEFAULT 'Asia/Dubai',
    auto_approve_enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    auto_approve_min_rating        INTEGER      NOT NULL DEFAULT 9,
    auto_approve_require_no_flags  BOOLEAN      NOT NULL DEFAULT TRUE,
    spot_check_percent             INTEGER      NOT NULL DEFAULT 10,
    green_min_rating               INTEGER      NOT NULL DEFAULT 8,
    amber_min_rating               INTEGER      NOT NULL DEFAULT 4,
    needs_me_days_without_session  INTEGER      NOT NULL DEFAULT 2,
    weekly_review_day              VARCHAR(10)  NOT NULL DEFAULT 'SUNDAY',
    monthly_spend_limit_usd        NUMERIC(10, 2),
    last_run_date                  DATE,
    -- Set the first time the AI is switched on. Older submissions are only
    -- analysed when the admin asks, so switching on never bills for the backlog.
    analyse_from                   TIMESTAMPTZ,
    active                        BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_ai_settings_practice_provider   CHECK (practice_provider IN ('CLAUDE', 'GEMINI')),
    CONSTRAINT ck_ai_settings_reflection_provider CHECK (reflection_provider IN ('CLAUDE', 'GEMINI')),
    CONSTRAINT ck_ai_settings_effort              CHECK (claude_effort IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_ai_settings_call_mode           CHECK (call_mode IN ('BATCH', 'NORMAL')),
    CONSTRAINT ck_ai_settings_auto_rating         CHECK (auto_approve_min_rating BETWEEN 1 AND 10),
    CONSTRAINT ck_ai_settings_spot_check          CHECK (spot_check_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_ai_settings_colours             CHECK (amber_min_rating BETWEEN 1 AND 10
                                                         AND green_min_rating BETWEEN 1 AND 10
                                                         AND amber_min_rating < green_min_rating),
    CONSTRAINT ck_ai_settings_needs_me_days       CHECK (needs_me_days_without_session BETWEEN 1 AND 30)
);

CREATE UNIQUE INDEX uq_ai_settings_active ON ai_settings (active) WHERE active = TRUE AND is_deleted = FALSE;

INSERT INTO ai_settings (id) VALUES (gen_random_uuid());

-- ---------------------------------------------------------------------------
-- Reference pack: every version is kept; exactly one is active.
-- Editing, uploading or deleting a file creates a new version.
-- ---------------------------------------------------------------------------
CREATE TABLE ai_reference_pack_versions (
    id          UUID        PRIMARY KEY,
    version_no  INTEGER     NOT NULL,
    note        VARCHAR(500),
    active      BOOLEAN     NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_ai_ref_pack_version_no ON ai_reference_pack_versions (version_no);
CREATE UNIQUE INDEX uq_ai_ref_pack_active ON ai_reference_pack_versions (active) WHERE active = TRUE;

CREATE TABLE ai_reference_pack_files (
    id                  UUID         PRIMARY KEY,
    pack_version_id     UUID         NOT NULL,
    file_name           VARCHAR(200) NOT NULL,
    content             TEXT         NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_ai_ref_pack_file_version FOREIGN KEY (pack_version_id) REFERENCES ai_reference_pack_versions (id)
);

CREATE UNIQUE INDEX uq_ai_ref_pack_file_name ON ai_reference_pack_files (pack_version_id, LOWER(file_name));

-- ---------------------------------------------------------------------------
-- ai_batches: one provider-side batch (Claude Message Batch / Gemini batch job)
-- ---------------------------------------------------------------------------
CREATE TABLE ai_batches (
    id                 UUID         PRIMARY KEY,
    provider           VARCHAR(20)  NOT NULL,
    provider_batch_id  VARCHAR(255) NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    submitted_at       TIMESTAMPTZ  NOT NULL,
    deadline_at        TIMESTAMPTZ  NOT NULL,
    ended_at           TIMESTAMPTZ,
    request_count      INTEGER      NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_ai_batches_status CHECK (status IN ('SUBMITTED', 'ENDED', 'EXPIRED', 'FAILED'))
);

CREATE INDEX idx_ai_batches_status ON ai_batches (status);

-- ---------------------------------------------------------------------------
-- ai_jobs: one analysis of one practice session or reflection
-- ---------------------------------------------------------------------------
CREATE TABLE ai_jobs (
    id                         UUID         PRIMARY KEY,
    job_type                   VARCHAR(30)  NOT NULL,
    target_id                  UUID         NOT NULL,
    status                     VARCHAR(20)  NOT NULL,
    provider                   VARCHAR(20)  NOT NULL,
    model                      VARCHAR(100) NOT NULL,
    backup_used                BOOLEAN      NOT NULL DEFAULT FALSE,
    format_retries             INTEGER      NOT NULL DEFAULT 0,
    batch_id                   UUID,
    failure_reason             VARCHAR(30),
    failure_detail             TEXT,
    reference_pack_version_id  UUID,
    completed_at               TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_ai_jobs_batch FOREIGN KEY (batch_id) REFERENCES ai_batches (id),
    CONSTRAINT ck_ai_jobs_type CHECK (job_type IN ('PRACTICE_REVIEW', 'REFLECTION_REVIEW')),
    CONSTRAINT ck_ai_jobs_status CHECK (status IN ('QUEUED', 'SUBMITTED', 'SUCCEEDED', 'NOT_ANALYSED'))
);

CREATE INDEX idx_ai_jobs_target ON ai_jobs (target_id);
CREATE INDEX idx_ai_jobs_batch ON ai_jobs (batch_id);
CREATE INDEX idx_ai_jobs_status ON ai_jobs (status);

-- ---------------------------------------------------------------------------
-- ai_results: the AI's answer, saved exactly as returned plus the parsed copy.
-- A few headline values are copied into columns for filtering and sorting.
-- ---------------------------------------------------------------------------
CREATE TABLE ai_results (
    id                         UUID         PRIMARY KEY,
    job_id                     UUID         NOT NULL,
    job_type                   VARCHAR(30)  NOT NULL,
    target_id                  UUID         NOT NULL,
    provider                   VARCHAR(20)  NOT NULL,
    model                      VARCHAR(100) NOT NULL,
    reference_pack_version_id  UUID,
    raw_response               TEXT         NOT NULL,
    result_json                TEXT         NOT NULL,
    recommendation             VARCHAR(30),
    trust_rating               INTEGER,
    engagement_rating          INTEGER,
    flag_count                 INTEGER      NOT NULL DEFAULT 0,
    needs_attention            BOOLEAN      NOT NULL DEFAULT FALSE,
    input_tokens               BIGINT,
    output_tokens              BIGINT,
    cache_read_tokens          BIGINT,
    cache_write_tokens         BIGINT,
    via_batch                  BOOLEAN      NOT NULL DEFAULT FALSE,
    is_current                 BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_ai_results_job FOREIGN KEY (job_id) REFERENCES ai_jobs (id)
);

CREATE INDEX idx_ai_results_target ON ai_results (target_id) WHERE is_current = TRUE;

-- ---------------------------------------------------------------------------
-- Practice sessions: unlimited transcripts, numbered turns, auto-approve
-- ---------------------------------------------------------------------------
ALTER TABLE practice_sessions ADD COLUMN transcript_turns    TEXT;
ALTER TABLE practice_sessions ADD COLUMN turn_count          INTEGER;
ALTER TABLE practice_sessions ADD COLUMN transcript_chars    INTEGER;
ALTER TABLE practice_sessions ADD COLUMN turn_split_method   VARCHAR(20);
ALTER TABLE practice_sessions ADD COLUMN auto_approved       BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE practice_sessions ADD COLUMN auto_approved_at    TIMESTAMPTZ;
ALTER TABLE practice_sessions ADD COLUMN spot_check          BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE practice_sessions ADD COLUMN ai_override_reason  VARCHAR(500);
-- Copy of the current AI headline, for filtering and sorting the review list.
-- The full result lives in ai_results; the student's submission is never changed.
ALTER TABLE practice_sessions ADD COLUMN ai_status           VARCHAR(20);
ALTER TABLE practice_sessions ADD COLUMN ai_trust_rating     INTEGER;
ALTER TABLE practice_sessions ADD COLUMN ai_recommendation   VARCHAR(30);
ALTER TABLE practice_sessions ADD COLUMN ai_flag_count       INTEGER;

CREATE INDEX idx_practice_ai_status ON practice_sessions (ai_status);

-- ---------------------------------------------------------------------------
-- Reflections: voice-note transcription status (audio itself is never changed)
-- ---------------------------------------------------------------------------
ALTER TABLE reflection_entries ADD COLUMN transcript_status  VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE reflection_entries ADD COLUMN transcript_model   VARCHAR(100);
ALTER TABLE reflection_entries ADD COLUMN transcribed_at     TIMESTAMPTZ;

ALTER TABLE reflection_entries ADD COLUMN ai_status           VARCHAR(20);
ALTER TABLE reflection_entries ADD COLUMN ai_flag_count       INTEGER;
ALTER TABLE reflection_entries ADD COLUMN ai_needs_attention  BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE reflection_entries ADD CONSTRAINT ck_reflection_transcript_status
    CHECK (transcript_status IN ('NONE', 'DONE', 'FAILED'));

-- ---------------------------------------------------------------------------
-- Student code-names: the AI only ever sees these, never real names
-- ---------------------------------------------------------------------------
CREATE SEQUENCE student_code_seq START 1;

ALTER TABLE users ADD COLUMN code_name VARCHAR(20);

WITH ordered AS (
    SELECT u2.id, ROW_NUMBER() OVER (ORDER BY u2.created_at, u2.id) AS rn
    FROM users u2
    JOIN roles r ON r.id = u2.role_id
    WHERE r.name = 'STUDENT'
)
UPDATE users u
SET code_name = 'S-' || LPAD(ordered.rn::TEXT, 4, '0')
FROM ordered
WHERE u.id = ordered.id;

SELECT setval('student_code_seq',
              (SELECT COUNT(*) + 1 FROM users u JOIN roles r ON r.id = u.role_id WHERE r.name = 'STUDENT'),
              false);

CREATE UNIQUE INDEX uq_users_code_name ON users (code_name) WHERE code_name IS NOT NULL;
