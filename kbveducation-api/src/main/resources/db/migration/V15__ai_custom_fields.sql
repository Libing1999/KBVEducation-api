-- ============================================================================
-- Custom AI fields (memo "AI plan" 17.10.2026, 2.1)
--
-- The admin adds a field (name, plain description, type) and the AI returns it
-- from the next run. The fixed reply format is built from this list each run.
-- A new field has data only for items analysed after it was added; older items
-- can be analysed again on request.
-- ============================================================================

CREATE TABLE ai_field_definitions (
    id             UUID         PRIMARY KEY,
    job_type       VARCHAR(30)  NOT NULL,
    field_key      VARCHAR(60)  NOT NULL,
    label          VARCHAR(120) NOT NULL,
    description    VARCHAR(1000) NOT NULL,
    field_type     VARCHAR(20)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order  INTEGER      NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_ai_field_job_type CHECK (job_type IN ('PRACTICE_REVIEW', 'REFLECTION_REVIEW')),
    CONSTRAINT ck_ai_field_type CHECK (field_type IN ('NUMBER', 'RATING', 'YES_NO', 'SHORT_TEXT', 'LIST'))
);

-- A key is never reused for the same kind of item, even after the field is switched off,
-- so saved results always keep one meaning per key.
CREATE UNIQUE INDEX uq_ai_field_key ON ai_field_definitions (job_type, field_key);
