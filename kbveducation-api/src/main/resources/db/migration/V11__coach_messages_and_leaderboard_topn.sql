-- ============================================================================
-- KBV Education — coach messages (Live Action / "Messages from Bhavya") +
-- admin-configurable public leaderboard top-N.
-- Additive only. UUID PKs, full audit block + soft delete (mirrors Phase 1-4).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- score_config.public_top_n — how many top-ranked students are ever shown
-- publicly on the student leaderboard (default 3, matches the approved
-- design's "Top three" panel). Never hardcoded in application code — always
-- read from this column.
-- ---------------------------------------------------------------------------
ALTER TABLE score_config ADD COLUMN public_top_n INTEGER NOT NULL DEFAULT 3;
ALTER TABLE score_config ADD CONSTRAINT ck_score_config_top_n CHECK (public_top_n BETWEEN 1 AND 50);

-- ---------------------------------------------------------------------------
-- coach_message — a hand-sent note from staff ("Bhavya"), either addressed to
-- one student (INDIVIDUAL) or an entire cohort (COLLECTIVE). Surfaces
-- read-only in the student Leaderboard's "Live Action" drawer and the parent
-- "Messages from Bhavya" card. No auto-generated system notifications live
-- here — see the separate `notifications` table (Notification entity) for
-- those; this is the one manual channel.
-- ---------------------------------------------------------------------------
CREATE TABLE coach_message (
    id                 UUID          PRIMARY KEY,
    sender_id          UUID          NOT NULL,
    target_type        VARCHAR(20)   NOT NULL,
    target_student_id  UUID,
    target_cohort_id   UUID,
    tag                VARCHAR(60)   NOT NULL,
    body               VARCHAR(2000) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_coach_message_sender  FOREIGN KEY (sender_id)         REFERENCES users (id),
    CONSTRAINT fk_coach_message_student FOREIGN KEY (target_student_id) REFERENCES users (id),
    CONSTRAINT fk_coach_message_cohort  FOREIGN KEY (target_cohort_id)  REFERENCES cohorts (id),
    CONSTRAINT ck_coach_message_type    CHECK (target_type IN ('INDIVIDUAL', 'COLLECTIVE')),
    CONSTRAINT ck_coach_message_target  CHECK (
        (target_type = 'INDIVIDUAL' AND target_student_id IS NOT NULL AND target_cohort_id IS NULL) OR
        (target_type = 'COLLECTIVE' AND target_cohort_id IS NOT NULL AND target_student_id IS NULL)
    )
);
CREATE INDEX idx_coach_message_student ON coach_message (target_student_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_coach_message_cohort  ON coach_message (target_cohort_id)  WHERE is_deleted = FALSE;
CREATE INDEX idx_coach_message_created ON coach_message (created_at DESC);

-- ---------------------------------------------------------------------------
-- coach_message_read — per-recipient read state. `reader_id` is whichever
-- account actually viewed the message: the student themself, or a parent
-- viewing their linked student's copy — deliberately separate rows, so a
-- parent opening a collective cohort message never marks it read for the
-- student (or any other parent/student in the same cohort), and vice versa.
-- ---------------------------------------------------------------------------
CREATE TABLE coach_message_read (
    id         UUID        PRIMARY KEY,
    message_id UUID        NOT NULL,
    reader_id  UUID        NOT NULL,
    read_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_cmr_message FOREIGN KEY (message_id) REFERENCES coach_message (id) ON DELETE CASCADE,
    CONSTRAINT fk_cmr_reader  FOREIGN KEY (reader_id)  REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_cmr_message_reader UNIQUE (message_id, reader_id)
);
