package com.kbv.education.service.ai;

import com.kbv.education.entity.PracticeSession;
import com.kbv.education.entity.enums.PracticeStatus;

/**
 * Extension point for deciding the initial review status of a logged practice
 * session.
 *
 * <p>Phase 3 ships {@link ManualPracticeValidationService}, which always returns
 * {@link PracticeStatus#PENDING_REVIEW} — a human reviews every session. A future
 * AI-backed implementation can auto-approve/reject by replacing this bean, with
 * no change to controllers or the database schema.</p>
 */
public interface PracticeValidationService {

    /** Decide the status a newly logged session should start in. */
    PracticeStatus validate(PracticeSession session);

    /** Whether an automated validation backend is currently wired in. */
    boolean isAutomated();
}
