-- ============================================================================
-- KBV Education — Phase 4 scoring engine schema
-- Configurable score engine, graduation tiers, leaderboard, analytics, audit.
-- UUID PKs, full audit block + soft delete on every table (mirrors Phase 1-3).
-- Additive only — does not touch any Phase 1-3 table except the voided-day
-- columns appended to study_days at the bottom.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- score_config  (single active row; admin-editable weights/windows/toggles)
-- ---------------------------------------------------------------------------
CREATE TABLE score_config (
    id                          UUID         PRIMARY KEY,
    practice_weight             NUMERIC(5,2) NOT NULL DEFAULT 60.00,
    reflection_weight           NUMERIC(5,2) NOT NULL DEFAULT 20.00,
    homework_weight             NUMERIC(5,2) NOT NULL DEFAULT 10.00,
    quiz_weight                 NUMERIC(5,2) NOT NULL DEFAULT 10.00,
    practice_window_start       DATE,
    reflection_window_start     DATE,
    reflection_window_end       DATE,
    total_reflection_days       INTEGER      NOT NULL DEFAULT 90,
    total_homework_count        INTEGER      NOT NULL DEFAULT 10,
    leaderboard_enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    leaderboard_sort_by         VARCHAR(20)  NOT NULL DEFAULT 'COMPOSITE',
    dashboard_widgets_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    active                      BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_score_config_sort         CHECK (leaderboard_sort_by IN ('COMPOSITE', 'PRACTICE', 'QUIZ', 'REFLECTION', 'HOMEWORK')),
    CONSTRAINT ck_score_config_weights_sum  CHECK (practice_weight + reflection_weight + homework_weight + quiz_weight = 100.00),
    CONSTRAINT ck_score_config_weights_range CHECK (
        practice_weight BETWEEN 0 AND 100 AND reflection_weight BETWEEN 0 AND 100 AND
        homework_weight BETWEEN 0 AND 100 AND quiz_weight BETWEEN 0 AND 100)
);

-- Only one active config row at a time.
CREATE UNIQUE INDEX uq_score_config_active ON score_config (active) WHERE active = TRUE AND is_deleted = FALSE;

-- Seed the default (single) active config row per the spec's defaults.
INSERT INTO score_config (id, practice_weight, reflection_weight, homework_weight, quiz_weight)
VALUES (gen_random_uuid(), 60.00, 20.00, 10.00, 10.00);

-- ---------------------------------------------------------------------------
-- student_scores  (append-only calculation history; "current" = latest is_current row)
-- ---------------------------------------------------------------------------
CREATE TABLE student_scores (
    id                    UUID         PRIMARY KEY,
    student_id            UUID         NOT NULL,
    cohort_id             UUID,
    practice_percentage   NUMERIC(5,2) NOT NULL,
    reflection_percentage NUMERIC(5,2) NOT NULL,
    homework_percentage   NUMERIC(5,2) NOT NULL,
    quiz_percentage       NUMERIC(5,2) NOT NULL,
    composite_score       NUMERIC(5,2) NOT NULL,
    practice_weight       NUMERIC(5,2) NOT NULL,
    reflection_weight     NUMERIC(5,2) NOT NULL,
    homework_weight       NUMERIC(5,2) NOT NULL,
    quiz_weight           NUMERIC(5,2) NOT NULL,
    trigger_reason        VARCHAR(30)  NOT NULL,
    is_current            BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_student_scores_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_student_scores_cohort  FOREIGN KEY (cohort_id)  REFERENCES cohorts (id),
    CONSTRAINT ck_student_scores_trigger CHECK (trigger_reason IN
        ('PRACTICE_CHANGE', 'REFLECTION_CHANGE', 'HOMEWORK_CHANGE', 'QUIZ_CHANGE', 'CONFIG_CHANGE', 'MANUAL_RECALC')),
    CONSTRAINT ck_student_scores_range CHECK (
        practice_percentage BETWEEN 0 AND 100 AND reflection_percentage BETWEEN 0 AND 100 AND
        homework_percentage BETWEEN 0 AND 100 AND quiz_percentage BETWEEN 0 AND 100 AND
        composite_score BETWEEN 0 AND 100)
);

CREATE UNIQUE INDEX uq_student_scores_current ON student_scores (student_id) WHERE is_current = TRUE AND is_deleted = FALSE;
CREATE INDEX idx_student_scores_student_date ON student_scores (student_id, created_at DESC);
CREATE INDEX idx_student_scores_cohort ON student_scores (cohort_id) WHERE is_current = TRUE AND is_deleted = FALSE;

-- ---------------------------------------------------------------------------
-- tier_rules  (configurable graduation thresholds, ordered by tier_rank; 1 = best)
-- ---------------------------------------------------------------------------
CREATE TABLE tier_rules (
    id                       UUID         PRIMARY KEY,
    tier_name                VARCHAR(30)  NOT NULL,
    tier_rank                INTEGER      NOT NULL,
    min_composite            NUMERIC(5,2) NOT NULL,
    max_composite            NUMERIC(5,2),
    min_practice_percentage  NUMERIC(5,2) NOT NULL DEFAULT 0,
    min_full_papers          INTEGER      NOT NULL DEFAULT 0,
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_tier_rules_composite_range CHECK (
        min_composite BETWEEN 0 AND 100 AND (max_composite IS NULL OR max_composite BETWEEN 0 AND 100))
);

CREATE UNIQUE INDEX uq_tier_rules_rank ON tier_rules (tier_rank) WHERE active = TRUE AND is_deleted = FALSE;

-- Seed the spec's default tiers.
INSERT INTO tier_rules (id, tier_name, tier_rank, min_composite, max_composite, min_practice_percentage, min_full_papers) VALUES
    (gen_random_uuid(), 'Tier 1',       1, 90.00, NULL,  88.00, 12),
    (gen_random_uuid(), 'Tier 2',       2, 80.00, 89.99, 83.00, 6),
    (gen_random_uuid(), 'Tier 3',       3, 60.00, 79.99, 71.00, 0),
    (gen_random_uuid(), 'Not Passing',  4, 0.00,  59.99, 0.00,  0);

-- ---------------------------------------------------------------------------
-- tier_history  (append-only log; latest row per student = current calculated
-- state, latest row with confirmed_tier set = current confirmed/override state)
-- ---------------------------------------------------------------------------
CREATE TABLE tier_history (
    id                    UUID         PRIMARY KEY,
    student_id            UUID         NOT NULL,
    calculated_tier       VARCHAR(30)  NOT NULL,
    confirmed_tier        VARCHAR(30),
    is_override           BOOLEAN      NOT NULL DEFAULT FALSE,
    override_reason       TEXT,
    composite_score       NUMERIC(5,2) NOT NULL,
    practice_percentage   NUMERIC(5,2) NOT NULL,
    full_papers_count     INTEGER      NOT NULL DEFAULT 0,
    decided_by            UUID,
    source                VARCHAR(20)  NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_tier_history_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_tier_history_decider FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT ck_tier_history_source  CHECK (source IN ('SYSTEM', 'ADMIN_CONFIRM', 'ADMIN_OVERRIDE'))
);

CREATE INDEX idx_tier_history_student_date ON tier_history (student_id, created_at DESC);
CREATE INDEX idx_tier_history_student_confirmed ON tier_history (student_id, created_at DESC) WHERE confirmed_tier IS NOT NULL;

-- ---------------------------------------------------------------------------
-- leaderboard_snapshot  (cached, per-cohort, per-sort-metric rankings)
-- ---------------------------------------------------------------------------
CREATE TABLE leaderboard_snapshot (
    id                     UUID         PRIMARY KEY,
    cohort_id              UUID         NOT NULL,
    student_id             UUID         NOT NULL,
    rank                   INTEGER      NOT NULL,
    composite_score        NUMERIC(5,2) NOT NULL,
    practice_percentage    NUMERIC(5,2) NOT NULL,
    reflection_percentage  NUMERIC(5,2) NOT NULL,
    homework_percentage    NUMERIC(5,2) NOT NULL,
    quiz_percentage        NUMERIC(5,2) NOT NULL,
    current_tier           VARCHAR(30),
    sort_by                VARCHAR(20)  NOT NULL,
    generated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_leaderboard_cohort  FOREIGN KEY (cohort_id)  REFERENCES cohorts (id),
    CONSTRAINT fk_leaderboard_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT ck_leaderboard_sort    CHECK (sort_by IN ('COMPOSITE', 'PRACTICE', 'QUIZ', 'REFLECTION', 'HOMEWORK'))
);

-- Prevent duplicate leaderboard entries (spec's Validation section).
CREATE UNIQUE INDEX uq_leaderboard_cohort_student_sort
    ON leaderboard_snapshot (cohort_id, student_id, sort_by) WHERE is_deleted = FALSE;
CREATE INDEX idx_leaderboard_cohort_sort_rank ON leaderboard_snapshot (cohort_id, sort_by, rank);

-- ---------------------------------------------------------------------------
-- dashboard_metrics  (cached admin analytics aggregates, global or per-cohort)
-- ---------------------------------------------------------------------------
CREATE TABLE dashboard_metrics (
    id           UUID          PRIMARY KEY,
    cohort_id    UUID,
    metric_key   VARCHAR(50)   NOT NULL,
    metric_value NUMERIC(12,2) NOT NULL,
    computed_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_dashboard_metrics_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts (id)
);

-- One row per (cohort, metric). COALESCE handles the null cohort_id of global rows.
CREATE UNIQUE INDEX uq_dashboard_metrics_key ON dashboard_metrics (
    COALESCE(cohort_id, '00000000-0000-0000-0000-000000000000'::uuid), metric_key
) WHERE is_deleted = FALSE;

-- ---------------------------------------------------------------------------
-- score_audit_logs  (every score-related change: weights, overrides, approvals...)
-- ---------------------------------------------------------------------------
CREATE TABLE score_audit_logs (
    id             UUID        PRIMARY KEY,
    entity_type    VARCHAR(30) NOT NULL,
    entity_id      UUID,
    student_id     UUID,
    action         VARCHAR(50) NOT NULL,
    previous_value TEXT,
    new_value      TEXT,
    reason         TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_score_audit_student FOREIGN KEY (student_id) REFERENCES users (id)
);

CREATE INDEX idx_score_audit_entity ON score_audit_logs (entity_type, entity_id);
CREATE INDEX idx_score_audit_student_date ON score_audit_logs (student_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- study_days — additive columns for admin-voided days (excluded from Practice %
-- / Reflection % numerators and denominators).
-- ---------------------------------------------------------------------------
ALTER TABLE study_days ADD COLUMN is_voided     BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE study_days ADD COLUMN voided_reason TEXT;
ALTER TABLE study_days ADD COLUMN voided_by     UUID REFERENCES users (id);
ALTER TABLE study_days ADD COLUMN voided_at     TIMESTAMPTZ;
