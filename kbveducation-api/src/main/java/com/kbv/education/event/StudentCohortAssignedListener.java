package com.kbv.education.event;

import com.kbv.education.service.email.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the cohort-assignment event to email delivery. AFTER_COMMIT
 * guarantees the mail only goes out for assignments that actually persisted
 * (a rolled-back transaction sends nothing), and @Async moves the SMTP
 * round-trip off the request thread so the admin's action returns
 * immediately. The notification service itself never throws, so a delivery
 * failure cannot affect anything upstream.
 */
@Component
@RequiredArgsConstructor
public class StudentCohortAssignedListener {

    private final EmailNotificationService emailNotificationService;

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStudentAssigned(StudentCohortAssignedEvent event) {
        emailNotificationService.sendCohortAssignmentEmail(event.studentId(), event.cohortId());
    }
}
