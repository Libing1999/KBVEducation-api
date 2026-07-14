package com.kbv.education.service.ai;

import com.kbv.education.entity.PracticeSession;
import com.kbv.education.entity.enums.PracticeStatus;
import org.springframework.stereotype.Service;

/**
 * Default, no-AI implementation: every logged session starts as
 * {@code PENDING_REVIEW} and is reviewed manually by an admin. Replace this bean
 * to enable automatic approval/rejection later.
 */
@Service
public class ManualPracticeValidationService implements PracticeValidationService {

    @Override
    public PracticeStatus validate(PracticeSession session) {
        return PracticeStatus.PENDING_REVIEW;
    }

    @Override
    public boolean isAutomated() {
        return false;
    }
}
