-- ============================================================================
-- Corrects export_history's dataset CHECK constraint (added in V7) to also
-- cover the two Phase 4 export endpoints that don't map onto a Step 3
-- generic-registry name: exportTiers -> TIER_HISTORY, exportStudentProgress
-- -> PROGRESS. LEADERBOARD/COMPOSITE_SCORES already covered exportLeaderboard
-- /exportScores correctly.
-- ============================================================================
ALTER TABLE export_history DROP CONSTRAINT ck_export_history_dataset;

ALTER TABLE export_history ADD CONSTRAINT ck_export_history_dataset CHECK (dataset IN (
    'LEADERBOARD', 'COMPOSITE_SCORES', 'TIER_HISTORY', 'PROGRESS',
    'STUDENTS', 'PARENTS', 'COHORTS', 'LESSONS',
    'HOMEWORK', 'QUIZZES', 'REFLECTIONS', 'PRACTICE_LOGS', 'ANALYTICS', 'AUDIT_LOGS'));
