-- ============================================================================
-- KBV Education — Phase 3 daily-activity schema
-- Reflections, practice logging, review workflow, study days, statistics,
-- activity timeline.
-- UUID PKs, full audit block + soft delete on every table (mirrors Phase 1/2).
-- Additive only — does not touch any Phase 1 or Phase 2 table.
--
-- Supporting tables beyond the enumerated list (required by the features,
-- same pattern as Phase 2's `homework` / `homework_submission_files`):
--   * reflection_questions  — admin-configurable daily questions (never hardcoded)
--   * reflection_answers    — per-question typed answers (questions are dynamic)
--   * practice_files        — optional practice attachments
--
-- No migration is needed for notifications: `notifications.type` and
-- `reference_type` are free VARCHARs (no CHECK), so Phase 3 adds new enum
-- values in Java only. `activity_logs.activity_type` and
-- `dashboard_statistics.metric` are likewise left free for forward-compat.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- reflection_questions  (admin-configured, global daily questions)
-- ---------------------------------------------------------------------------
CREATE TABLE reflection_questions (
    id            UUID        PRIMARY KEY,
    question_text TEXT        NOT NULL,
    display_order INTEGER     NOT NULL DEFAULT 0,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    is_deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    version       BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_reflection_questions_order ON reflection_questions (enabled, display_order);

-- ---------------------------------------------------------------------------
-- reflection_entries  (one per student per day)
-- Audio is stored as-is; NO speech-to-text is performed. The stored file is
-- kept for future AI transcription.
-- ---------------------------------------------------------------------------
CREATE TABLE reflection_entries (
    id                UUID         PRIMARY KEY,
    student_id        UUID         NOT NULL,
    reflection_date   DATE         NOT NULL,
    reflection_type   VARCHAR(20)  NOT NULL DEFAULT 'TYPED',  -- TYPED / VOICE / BOTH

    audio_file_name   VARCHAR(255),           -- original name shown to users
    audio_stored_name VARCHAR(255),           -- unique name on disk (never exposed)
    audio_file_type   VARCHAR(100),
    audio_file_size   BIGINT,

    submitted_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_reflections_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT ck_reflections_type    CHECK (reflection_type IN ('TYPED', 'VOICE', 'BOTH'))
);

-- One reflection per student per day.
CREATE UNIQUE INDEX uq_reflections_student_date
    ON reflection_entries (student_id, reflection_date) WHERE is_deleted = FALSE;
CREATE INDEX idx_reflections_student ON reflection_entries (student_id);
CREATE INDEX idx_reflections_date    ON reflection_entries (reflection_date DESC);
-- Unique stored audio name (only when an audio file is present).
CREATE UNIQUE INDEX uq_reflections_audio_stored
    ON reflection_entries (audio_stored_name) WHERE audio_stored_name IS NOT NULL;

-- ---------------------------------------------------------------------------
-- reflection_answers  (typed answer per configured question)
-- ---------------------------------------------------------------------------
CREATE TABLE reflection_answers (
    id                  UUID        PRIMARY KEY,
    reflection_entry_id UUID        NOT NULL,
    question_id         UUID        NOT NULL,
    answer_text         TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    is_deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    version             BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_reflection_answers_entry    FOREIGN KEY (reflection_entry_id) REFERENCES reflection_entries (id),
    CONSTRAINT fk_reflection_answers_question FOREIGN KEY (question_id)         REFERENCES reflection_questions (id)
);

CREATE INDEX idx_reflection_answers_entry ON reflection_answers (reflection_entry_id);

-- ---------------------------------------------------------------------------
-- practice_sessions  (student study log; reviewed manually by admin)
-- ---------------------------------------------------------------------------
CREATE TABLE practice_sessions (
    id               UUID         PRIMARY KEY,
    student_id       UUID         NOT NULL,
    study_date       DATE         NOT NULL,
    subject          VARCHAR(200) NOT NULL,
    duration_minutes INTEGER      NOT NULL,
    study_type       VARCHAR(30)  NOT NULL,   -- PAST_PAPER / WEAKNESS_PRACTICE / GENERAL_PRACTICE
    notes            TEXT,                     -- transcript / notes
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW',
    admin_comment    TEXT,
    reviewed_by      UUID,
    reviewed_at      TIMESTAMPTZ,

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_practice_student  FOREIGN KEY (student_id)  REFERENCES users (id),
    CONSTRAINT fk_practice_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT ck_practice_type     CHECK (study_type IN ('PAST_PAPER', 'WEAKNESS_PRACTICE', 'GENERAL_PRACTICE')),
    CONSTRAINT ck_practice_status   CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_practice_duration CHECK (duration_minutes > 0)
);

CREATE INDEX idx_practice_student ON practice_sessions (student_id, study_date DESC);
CREATE INDEX idx_practice_status  ON practice_sessions (status);

-- ---------------------------------------------------------------------------
-- practice_files  (optional attachments on a practice session)
-- ---------------------------------------------------------------------------
CREATE TABLE practice_files (
    id                  UUID         PRIMARY KEY,
    practice_session_id UUID         NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    stored_name         VARCHAR(255) NOT NULL,
    file_type           VARCHAR(100),
    file_size           BIGINT,
    uploaded_date       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_practice_files_session FOREIGN KEY (practice_session_id) REFERENCES practice_sessions (id),
    CONSTRAINT uq_practice_files_stored  UNIQUE (stored_name)
);

CREATE INDEX idx_practice_files_session ON practice_files (practice_session_id);

-- ---------------------------------------------------------------------------
-- practice_review_requests  (re-review requested after a rejection; history kept)
-- ---------------------------------------------------------------------------
CREATE TABLE practice_review_requests (
    id                  UUID         PRIMARY KEY,
    practice_session_id UUID         NOT NULL,
    student_id          UUID         NOT NULL,
    reason              TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED
    admin_notes         TEXT,
    resolved_by         UUID,
    resolved_at         TIMESTAMPTZ,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_review_requests_session  FOREIGN KEY (practice_session_id) REFERENCES practice_sessions (id),
    CONSTRAINT fk_review_requests_student  FOREIGN KEY (student_id)          REFERENCES users (id),
    CONSTRAINT fk_review_requests_resolver FOREIGN KEY (resolved_by)         REFERENCES users (id),
    CONSTRAINT ck_review_requests_status   CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_review_requests_session ON practice_review_requests (practice_session_id);
CREATE INDEX idx_review_requests_status  ON practice_review_requests (status);

-- ---------------------------------------------------------------------------
-- study_days  (per-student per-day activity rollup — powers the calendar)
-- ---------------------------------------------------------------------------
CREATE TABLE study_days (
    id             UUID        PRIMARY KEY,
    student_id     UUID        NOT NULL,
    study_date     DATE        NOT NULL,
    has_reflection BOOLEAN     NOT NULL DEFAULT FALSE,
    has_practice   BOOLEAN     NOT NULL DEFAULT FALSE,
    has_homework   BOOLEAN     NOT NULL DEFAULT FALSE,
    has_quiz       BOOLEAN     NOT NULL DEFAULT FALSE,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    is_deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    version        BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_study_days_student FOREIGN KEY (student_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX uq_study_days_student_date
    ON study_days (student_id, study_date) WHERE is_deleted = FALSE;
CREATE INDEX idx_study_days_student ON study_days (student_id, study_date DESC);

-- ---------------------------------------------------------------------------
-- dashboard_statistics  (daily metric snapshots, cached for fast dashboards)
-- Flexible metric rows so new metrics (incl. future AI signals) need no schema
-- change. scope = STUDENT (student_id set) or GLOBAL (student_id null).
-- ---------------------------------------------------------------------------
CREATE TABLE dashboard_statistics (
    id         UUID        PRIMARY KEY,
    scope      VARCHAR(20) NOT NULL,   -- STUDENT / GLOBAL
    student_id UUID,                    -- null when scope = GLOBAL
    stat_date  DATE        NOT NULL,
    metric     VARCHAR(50) NOT NULL,
    value      INTEGER     NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_dashboard_stats_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT ck_dashboard_stats_scope   CHECK (scope IN ('STUDENT', 'GLOBAL'))
);

-- One row per (scope, student, day, metric). COALESCE handles the null
-- student_id of GLOBAL rows so they de-duplicate correctly.
CREATE UNIQUE INDEX uq_dashboard_stats_key ON dashboard_statistics (
    scope,
    COALESCE(student_id, '00000000-0000-0000-0000-000000000000'::uuid),
    stat_date,
    metric
) WHERE is_deleted = FALSE;
CREATE INDEX idx_dashboard_stats_scope_date ON dashboard_statistics (scope, stat_date DESC);

-- ---------------------------------------------------------------------------
-- activity_logs  (student activity timeline — newest first)
-- activity_type is a free VARCHAR (no CHECK) so new activities can be added
-- without a migration.
-- ---------------------------------------------------------------------------
CREATE TABLE activity_logs (
    id             UUID         PRIMARY KEY,
    student_id     UUID         NOT NULL,
    activity_type  VARCHAR(40)  NOT NULL,   -- REFLECTION_SUBMITTED / PRACTICE_LOGGED / HOMEWORK_SUBMITTED / QUIZ_COMPLETED / REVIEW_APPROVED / ...
    title          VARCHAR(200) NOT NULL,
    description    VARCHAR(500),
    reference_type VARCHAR(30),             -- REFLECTION / PRACTICE / HOMEWORK / QUIZ / LESSON
    reference_id   UUID,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    version        BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_activity_logs_student FOREIGN KEY (student_id) REFERENCES users (id)
);

CREATE INDEX idx_activity_logs_student ON activity_logs (student_id, occurred_at DESC);
