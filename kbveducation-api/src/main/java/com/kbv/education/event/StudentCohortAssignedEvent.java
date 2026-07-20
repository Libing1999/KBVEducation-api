package com.kbv.education.event;

import java.util.UUID;

/**
 * Published from the single cohort-assignment choke point
 * (StudentServiceImpl.assignInternal) after a real assignment — first
 * assignment and cohort-to-cohort moves alike, but never for the
 * already-in-this-cohort no-op, cohort edits, profile updates, or parent
 * linking. Consumed AFTER_COMMIT, so listeners only ever see assignments
 * that actually persisted.
 */
public record StudentCohortAssignedEvent(UUID studentId, UUID cohortId) {
}
