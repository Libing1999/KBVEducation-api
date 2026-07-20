package com.kbv.education.service.email;

import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.EmailLog;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.EmailStatus;
import com.kbv.education.entity.enums.LogSeverity;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.repository.EmailLogRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.ApplicationLogService;
import com.kbv.education.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Event-level email orchestration: resolves the data for a notification,
 * renders the template, sends, and records every stage in {@code email_logs}
 * (QUEUED → SENT/FAILED, or SKIPPED when SMTP isn't configured). A failure is
 * additionally written to the audit trail and the error-monitoring feed —
 * and never propagated: by the time this runs, the triggering transaction
 * has already committed, and email delivery must not disturb it.
 *
 * <p>Future events (account created, password reset, lesson published,
 * homework/quiz reminders, certificates, parent notifications) become new
 * methods here following the same record→render→send→update pattern.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private static final String EVENT_COHORT_ASSIGNED = "COHORT_ASSIGNED";
    private static final String EVENT_TEST = "TEST_EMAIL";

    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final EmailLogRepository emailLogRepository;
    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final AuditLogService auditLogService;
    private final ApplicationLogService applicationLogService;

    /** Never throws. Runs on the email executor, after the assignment transaction committed. */
    public void sendCohortAssignmentEmail(UUID studentId, UUID cohortId) {
        User student;
        Cohort cohort;
        try {
            student = userRepository.findByIdAndDeletedFalse(studentId).orElse(null);
            cohort = cohortRepository.findByIdAndDeletedFalse(cohortId).orElse(null);
            if (student == null || cohort == null) {
                log.warn("Skipping cohort-assignment email: student {} or cohort {} no longer exists",
                        studentId, cohortId);
                return;
            }

            EmailTemplateService.RenderedEmail email = emailTemplateService.cohortAssignment(student, cohort);
            EmailLog entry = queued(student.getEmail(), email.subject(), EVENT_COHORT_ASSIGNED, studentId);
            log.info("Email queued: '{}' to {} (event {})", email.subject(), student.getEmail(), EVENT_COHORT_ASSIGNED);

            if (!emailService.isConfigured()) {
                markSkipped(entry);
                return;
            }
            try {
                emailService.send(student.getEmail(), email.subject(), email.html());
                markSent(entry);
            } catch (Exception sendFailure) {
                markFailed(entry, studentId, student.getEmail(), sendFailure);
            }
        } catch (Exception unexpected) {
            // Belt and braces: nothing in the email path may ripple anywhere.
            log.error("Cohort-assignment email flow failed unexpectedly for student {}", studentId, unexpected);
        }
    }

    /** Synchronous on purpose — the admin's Test Email button needs an immediate pass/fail. Throws on failure. */
    public void sendTestEmail(String recipient) throws Exception {
        EmailTemplateService.RenderedEmail email = emailTemplateService.testEmail();
        EmailLog entry = queued(recipient, email.subject(), EVENT_TEST, null);
        try {
            emailService.send(recipient, email.subject(), email.html());
            markSent(entry);
        } catch (Exception e) {
            markFailed(entry, null, recipient, e);
            throw e;
        }
    }

    private EmailLog queued(String recipient, String subject, String eventType, UUID studentId) {
        EmailLog entry = new EmailLog();
        entry.setRecipient(recipient);
        entry.setSubject(subject);
        entry.setEventType(eventType);
        entry.setStatus(EmailStatus.QUEUED);
        entry.setStudentId(studentId);
        return emailLogRepository.save(entry);
    }

    private void markSent(EmailLog entry) {
        entry.setStatus(EmailStatus.SENT);
        emailLogRepository.save(entry);
    }

    private void markSkipped(EmailLog entry) {
        entry.setStatus(EmailStatus.SKIPPED);
        entry.setErrorMessage("No SMTP host configured - email not sent");
        emailLogRepository.save(entry);
        log.warn("Email to {} skipped: no SMTP host configured", entry.getRecipient());
    }

    private void markFailed(EmailLog entry, UUID studentId, String recipient, Exception failure) {
        String reason = failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
        entry.setStatus(EmailStatus.FAILED);
        entry.setErrorMessage(reason);
        emailLogRepository.save(entry);
        log.error("Email '{}' to {} failed: {}", entry.getSubject(), recipient, reason, failure);
        // Audit trail entry (async thread: no request context, so IP/UA are null by design).
        auditLogService.record("EMAIL_FAILED", "EMAIL", studentId, recipient,
                null, entry.getEventType() + ": " + reason, null, null);
        // Error-monitoring feed, so the failure surfaces in the admin Application Logs page.
        applicationLogService.record(LogSeverity.WARNING, "EmailDeliveryFailure",
                "Email '" + entry.getSubject() + "' to " + recipient + " failed: " + reason,
                null, null, null, null);
    }
}
