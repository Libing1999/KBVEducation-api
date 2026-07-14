-- ============================================================================
-- KBV Education — Phase 3: forward-compat transcript column
-- Adds a nullable transcript column to reflection_entries so a future AI
-- transcription service can populate it WITHOUT any further schema change.
-- No AI is implemented now; the column stays null (manual transcription).
-- Additive only.
-- ============================================================================

ALTER TABLE reflection_entries ADD COLUMN transcript TEXT;
