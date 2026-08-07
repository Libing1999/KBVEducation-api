-- ============================================================================
-- KBV Education — Subject master data + Practice log enhancements
-- Adds an admin-managed `subjects` lookup table (same shape as
-- `reflection_questions`) and extends `practice_sessions` with a separate
-- `transcript` field and an optional `year` (for past-paper sessions).
--
-- Study Type: the existing 3 values (PAST_PAPER, WEAKNESS_PRACTICE,
-- GENERAL_PRACTICE) are kept valid for historical rows and for
-- `StudyType.PAST_PAPER`'s use in tier calculation; 5 new values are added
-- for the redesigned Log Practice dropdown. Additive only.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- subjects  (admin-configured, global subject list for the practice form)
-- ---------------------------------------------------------------------------
CREATE TABLE subjects (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    display_order INTEGER      NOT NULL DEFAULT 0,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_subjects_order ON subjects (enabled, display_order);
CREATE UNIQUE INDEX uq_subjects_name ON subjects (lower(name)) WHERE is_deleted = FALSE;

INSERT INTO subjects (id, name, display_order, enabled) VALUES
    (gen_random_uuid(), 'Biology',    0, TRUE),
    (gen_random_uuid(), 'Chemistry',  1, TRUE),
    (gen_random_uuid(), 'Physics',    2, TRUE),
    (gen_random_uuid(), 'Mathematics', 3, TRUE),
    (gen_random_uuid(), 'English',    4, TRUE),
    (gen_random_uuid(), 'Other',      5, TRUE);

-- ---------------------------------------------------------------------------
-- practice_sessions  additions
-- ---------------------------------------------------------------------------
ALTER TABLE practice_sessions ADD COLUMN transcript TEXT;
ALTER TABLE practice_sessions ADD COLUMN year INTEGER;
ALTER TABLE practice_sessions ADD CONSTRAINT ck_practice_year
    CHECK (year IS NULL OR year BETWEEN 2000 AND 2100);

ALTER TABLE practice_sessions DROP CONSTRAINT ck_practice_type;
ALTER TABLE practice_sessions ADD CONSTRAINT ck_practice_type CHECK (study_type IN (
    'PAST_PAPER', 'WEAKNESS_PRACTICE', 'GENERAL_PRACTICE',          -- legacy (historical rows only)
    'PAST_PAPER_TEST_DAY', 'PAST_PAPER_IMPROVEMENT_DAY',
    'TOPIC_STUDY', 'STRUCTURE_STUDY', 'OTHER'
));
