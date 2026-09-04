-- ============================================================================
-- Adds COHORT_DAY_CHANGE to student_scores.trigger_reason's allowed values —
-- V12's cohort day classification recalculates affected students' scores when
-- a date's Lesson/Rest/Skip Day classification changes.
-- ============================================================================

ALTER TABLE student_scores DROP CONSTRAINT ck_student_scores_trigger;
ALTER TABLE student_scores ADD CONSTRAINT ck_student_scores_trigger CHECK (trigger_reason IN
    ('PRACTICE_CHANGE', 'REFLECTION_CHANGE', 'HOMEWORK_CHANGE', 'QUIZ_CHANGE', 'CONFIG_CHANGE', 'COHORT_DAY_CHANGE', 'MANUAL_RECALC'));
