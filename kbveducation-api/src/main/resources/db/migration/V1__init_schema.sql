-- ============================================================================
-- KBV Education — Phase 1 initial schema
-- PostgreSQL. UUID primary keys, audit fields + soft delete on every table.
-- ============================================================================

-- gen_random_uuid() is built-in from PG13; extension kept for portability.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- roles
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),

    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_roles_name UNIQUE (name)
);

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    phone         VARCHAR(30),
    role_id       UUID         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ,

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_users_role   FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Email is unique among non-deleted users (case-insensitive).
CREATE UNIQUE INDEX uq_users_email_active
    ON users (LOWER(email))
    WHERE is_deleted = FALSE;

CREATE INDEX idx_users_role_id ON users (role_id);
CREATE INDEX idx_users_status  ON users (status);

-- ---------------------------------------------------------------------------
-- cohorts
-- ---------------------------------------------------------------------------
CREATE TABLE cohorts (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    description   TEXT,
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    exam_date     DATE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'UPCOMING',
    max_students  INTEGER      NOT NULL DEFAULT 0,

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_cohorts_status       CHECK (status IN ('UPCOMING', 'ACTIVE', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT ck_cohorts_max_students CHECK (max_students >= 0),
    CONSTRAINT ck_cohorts_dates        CHECK (end_date >= start_date)
);

CREATE INDEX idx_cohorts_status ON cohorts (status);

-- ---------------------------------------------------------------------------
-- student_cohort  (assignment of students into cohorts)
-- ---------------------------------------------------------------------------
CREATE TABLE student_cohort (
    id          UUID        PRIMARY KEY,
    student_id  UUID        NOT NULL,
    cohort_id   UUID        NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    active      BOOLEAN     NOT NULL DEFAULT TRUE,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    is_deleted  BOOLEAN     NOT NULL DEFAULT FALSE,
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_sc_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_sc_cohort  FOREIGN KEY (cohort_id)  REFERENCES cohorts (id),
    CONSTRAINT uq_sc_student_cohort UNIQUE (student_id, cohort_id)
);

-- Business rule: a student may have at most one ACTIVE cohort assignment.
CREATE UNIQUE INDEX uq_sc_one_active_cohort
    ON student_cohort (student_id)
    WHERE active = TRUE AND is_deleted = FALSE;

CREATE INDEX idx_sc_cohort_id ON student_cohort (cohort_id);

-- ---------------------------------------------------------------------------
-- parent_student  (links a parent user to a student user)
-- ---------------------------------------------------------------------------
CREATE TABLE parent_student (
    id         UUID        PRIMARY KEY,
    parent_id  UUID        NOT NULL,
    student_id UUID        NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_ps_parent  FOREIGN KEY (parent_id)  REFERENCES users (id),
    CONSTRAINT fk_ps_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT uq_ps_parent_student UNIQUE (parent_id, student_id)
);

CREATE INDEX idx_ps_parent_id  ON parent_student (parent_id);
CREATE INDEX idx_ps_student_id ON parent_student (student_id);

-- ---------------------------------------------------------------------------
-- refresh_tokens
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_rt_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_rt_user_id    ON refresh_tokens (user_id);
CREATE INDEX idx_rt_expires_at ON refresh_tokens (expires_at);

-- ---------------------------------------------------------------------------
-- user_sessions
-- ---------------------------------------------------------------------------
CREATE TABLE user_sessions (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL,
    refresh_token_id UUID,
    ip_address       VARCHAR(64),
    user_agent       VARCHAR(512),
    login_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_activity_at TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_us_user          FOREIGN KEY (user_id)          REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_us_refresh_token FOREIGN KEY (refresh_token_id) REFERENCES refresh_tokens (id) ON DELETE SET NULL
);

CREATE INDEX idx_us_user_id ON user_sessions (user_id);
CREATE INDEX idx_us_active  ON user_sessions (active);
