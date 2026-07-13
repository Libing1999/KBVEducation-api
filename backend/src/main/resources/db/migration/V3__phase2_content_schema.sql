-- ============================================================================
-- KBV Education — Phase 2 content schema
-- Lessons, files, quizzes, homework, notifications.
-- UUID PKs, full audit block + soft delete on every table (mirrors Phase 1).
-- Additive only — does not touch any Phase 1 table.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- lessons
-- ---------------------------------------------------------------------------
CREATE TABLE lessons (
    id             UUID         PRIMARY KEY,
    cohort_id      UUID         NOT NULL,
    lesson_number  INTEGER      NOT NULL,
    title          VARCHAR(200) NOT NULL,
    summary        TEXT,
    description    TEXT,
    lesson_date    DATE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_date TIMESTAMPTZ,
    display_order  INTEGER      NOT NULL DEFAULT 0,

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    version        BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_lessons_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts (id),
    CONSTRAINT ck_lessons_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE INDEX idx_lessons_cohort  ON lessons (cohort_id);
CREATE INDEX idx_lessons_status  ON lessons (status);
CREATE INDEX idx_lessons_order   ON lessons (cohort_id, display_order);

-- ---------------------------------------------------------------------------
-- lesson_files
-- ---------------------------------------------------------------------------
CREATE TABLE lesson_files (
    id            UUID         PRIMARY KEY,
    lesson_id     UUID         NOT NULL,
    file_name     VARCHAR(255) NOT NULL,   -- original name shown to users
    stored_name   VARCHAR(255) NOT NULL,   -- unique name on disk (never exposed)
    file_type     VARCHAR(100),            -- content type / extension
    file_size     BIGINT,                  -- bytes
    uploaded_date TIMESTAMPTZ  NOT NULL DEFAULT now(),

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_lesson_files_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT uq_lesson_files_stored UNIQUE (stored_name)
);

CREATE INDEX idx_lesson_files_lesson ON lesson_files (lesson_id);

-- ---------------------------------------------------------------------------
-- quizzes  (one per lesson)
-- ---------------------------------------------------------------------------
CREATE TABLE quizzes (
    id               UUID         PRIMARY KEY,
    lesson_id        UUID         NOT NULL,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    duration_minutes INTEGER,
    passing_marks    INTEGER,                 -- future use
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_quizzes_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT ck_quizzes_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

-- One active quiz per lesson.
CREATE UNIQUE INDEX uq_quizzes_lesson_active ON quizzes (lesson_id) WHERE is_deleted = FALSE;

-- ---------------------------------------------------------------------------
-- quiz_questions
-- ---------------------------------------------------------------------------
CREATE TABLE quiz_questions (
    id            UUID        PRIMARY KEY,
    quiz_id       UUID        NOT NULL,
    question_text TEXT        NOT NULL,
    question_type VARCHAR(20) NOT NULL,
    marks         INTEGER     NOT NULL DEFAULT 1,
    display_order INTEGER     NOT NULL DEFAULT 0,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    is_deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    version       BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_questions_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (id),
    CONSTRAINT ck_questions_type CHECK (question_type IN ('MCQ', 'OPEN_ENDED'))
);

CREATE INDEX idx_questions_quiz ON quiz_questions (quiz_id, display_order);

-- ---------------------------------------------------------------------------
-- quiz_options  (MCQ choices)
-- ---------------------------------------------------------------------------
CREATE TABLE quiz_options (
    id            UUID         PRIMARY KEY,
    question_id   UUID         NOT NULL,
    option_text   VARCHAR(500) NOT NULL,
    is_correct    BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order INTEGER      NOT NULL DEFAULT 0,

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_options_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id)
);

CREATE INDEX idx_options_question ON quiz_options (question_id, display_order);

-- ---------------------------------------------------------------------------
-- quiz_attempts  (one submission per student per quiz — no retake)
-- ---------------------------------------------------------------------------
CREATE TABLE quiz_attempts (
    id           UUID         PRIMARY KEY,
    quiz_id      UUID         NOT NULL,
    student_id   UUID         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    score        INTEGER,                    -- auto MCQ score (nullable)
    max_score    INTEGER,
    submitted_at TIMESTAMPTZ,

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_attempts_quiz    FOREIGN KEY (quiz_id)    REFERENCES quizzes (id),
    CONSTRAINT fk_attempts_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT ck_attempts_status  CHECK (status IN ('IN_PROGRESS', 'SUBMITTED'))
);

CREATE UNIQUE INDEX uq_attempts_quiz_student ON quiz_attempts (quiz_id, student_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_attempts_student ON quiz_attempts (student_id);

-- ---------------------------------------------------------------------------
-- quiz_answers
-- ---------------------------------------------------------------------------
CREATE TABLE quiz_answers (
    id                 UUID        PRIMARY KEY,
    attempt_id         UUID        NOT NULL,
    question_id        UUID        NOT NULL,
    selected_option_id UUID,                   -- for MCQ
    answer_text        TEXT,                   -- for open-ended
    is_correct         BOOLEAN,                -- MCQ auto-score result

    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    is_deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    version            BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_answers_attempt  FOREIGN KEY (attempt_id)         REFERENCES quiz_attempts (id),
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id)        REFERENCES quiz_questions (id),
    CONSTRAINT fk_answers_option   FOREIGN KEY (selected_option_id) REFERENCES quiz_options (id)
);

CREATE INDEX idx_answers_attempt ON quiz_answers (attempt_id);

-- ---------------------------------------------------------------------------
-- homework  (per-lesson configuration set by admin)
-- NOTE: not in the original enumerated list, but required to store the
-- admin-configured homework (title / instructions / due date / limits).
-- ---------------------------------------------------------------------------
CREATE TABLE homework (
    id                 UUID         PRIMARY KEY,
    lesson_id          UUID         NOT NULL,
    title              VARCHAR(200) NOT NULL,
    instructions       TEXT,
    due_date           TIMESTAMPTZ,
    allowed_file_types VARCHAR(255),           -- comma-separated extensions
    max_file_size_mb   INTEGER,

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_homework_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id)
);

CREATE UNIQUE INDEX uq_homework_lesson_active ON homework (lesson_id) WHERE is_deleted = FALSE;

-- ---------------------------------------------------------------------------
-- homework_submissions  (one per student per homework — submit once)
-- ---------------------------------------------------------------------------
CREATE TABLE homework_submissions (
    id           UUID         PRIMARY KEY,
    homework_id  UUID         NOT NULL,
    student_id   UUID         NOT NULL,
    note         TEXT,
    submitted_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_submissions_homework FOREIGN KEY (homework_id) REFERENCES homework (id),
    CONSTRAINT fk_submissions_student  FOREIGN KEY (student_id)  REFERENCES users (id)
);

CREATE UNIQUE INDEX uq_submissions_homework_student
    ON homework_submissions (homework_id, student_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_submissions_student ON homework_submissions (student_id);

-- ---------------------------------------------------------------------------
-- homework_submission_files  (multiple files per submission)
-- NOTE: not in the enumerated list, but required by "multiple files allowed".
-- ---------------------------------------------------------------------------
CREATE TABLE homework_submission_files (
    id            UUID         PRIMARY KEY,
    submission_id UUID         NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(255) NOT NULL,
    file_type     VARCHAR(100),
    file_size     BIGINT,
    uploaded_date TIMESTAMPTZ  NOT NULL DEFAULT now(),

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_submission_files_submission FOREIGN KEY (submission_id) REFERENCES homework_submissions (id),
    CONSTRAINT uq_submission_files_stored UNIQUE (stored_name)
);

CREATE INDEX idx_submission_files_submission ON homework_submission_files (submission_id);

-- ---------------------------------------------------------------------------
-- notifications
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id             UUID          PRIMARY KEY,
    recipient_id   UUID          NOT NULL,
    type           VARCHAR(50)   NOT NULL,   -- e.g. NEW_LESSON_PUBLISHED, HOMEWORK_SUBMITTED
    title          VARCHAR(200)  NOT NULL,
    message        VARCHAR(1000),
    is_read        BOOLEAN       NOT NULL DEFAULT FALSE,
    reference_type VARCHAR(50),              -- LESSON / QUIZ / HOMEWORK
    reference_id   UUID,

    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
    version        BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, is_read);
CREATE INDEX idx_notifications_created   ON notifications (recipient_id, created_at DESC);
