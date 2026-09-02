-- ============================================================================
-- KBV Education — cohort day classification (Lesson Day / Rest Day / Skip Day).
-- Additive only. UUID PK, full audit block + soft delete (mirrors Phase 1-4).
-- A date with no row here is a Lesson Day by default — existing cohorts with
-- no configured days behave exactly as before this migration.
-- ============================================================================

CREATE TABLE cohort_days (
    id         UUID        PRIMARY KEY,
    cohort_id  UUID        NOT NULL,
    date       DATE        NOT NULL,
    day_type   VARCHAR(20) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_cohort_day_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts (id),
    CONSTRAINT ck_cohort_day_type   CHECK (day_type IN ('LESSON_DAY', 'REST_DAY', 'SKIP_DAY'))
);

-- Partial unique index (not a table-level UNIQUE constraint) so a soft-deleted
-- row never blocks re-configuring the same cohort/date pair afterwards.
CREATE UNIQUE INDEX uq_cohort_day_cohort_date ON cohort_days (cohort_id, date) WHERE is_deleted = FALSE;
CREATE INDEX idx_cohort_day_cohort_range ON cohort_days (cohort_id, date) WHERE is_deleted = FALSE;
