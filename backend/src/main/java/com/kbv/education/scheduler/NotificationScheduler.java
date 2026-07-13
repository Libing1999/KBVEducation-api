package com.kbv.education.scheduler;

import com.kbv.education.entity.Homework;
import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.enums.NotificationType;
import com.kbv.education.entity.enums.ReferenceType;
import com.kbv.education.repository.HomeworkRepository;
import com.kbv.education.repository.HomeworkSubmissionRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled emitter for {@code HOMEWORK_DUE_TOMORROW} reminders. Runs daily and
 * notifies students (in the homework's cohort) who have not yet submitted homework
 * due the next day. In-app only — no push/email (Phase 2 scope).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "${app.notifications.homework-reminder-cron:0 0 8 * * *}")
    @Transactional
    public void sendHomeworkDueTomorrowReminders() {
        ZoneOffset utc = ZoneOffset.UTC;
        LocalDate tomorrow = LocalDate.now(utc).plusDays(1);
        Instant from = tomorrow.atStartOfDay(utc).toInstant();
        Instant to = tomorrow.plusDays(1).atStartOfDay(utc).toInstant();

        List<Homework> due = homeworkRepository.findByDueDateBetweenAndDeletedFalse(from, to);
        int sent = 0;
        for (Homework homework : due) {
            Lesson lesson = homework.getLesson();
            if (!lesson.isPublished() || lesson.isDeleted()) {
                continue;
            }
            for (var assignment : studentCohortRepository
                    .findByCohort_IdAndActiveTrueAndDeletedFalse(lesson.getCohort().getId())) {
                UUID studentId = assignment.getStudent().getId();
                if (!submissionRepository.existsByHomework_IdAndStudent_IdAndDeletedFalse(homework.getId(), studentId)) {
                    notificationService.notify(studentId, NotificationType.HOMEWORK_DUE_TOMORROW,
                            "Homework Due Tomorrow", homework.getTitle() + " is due tomorrow",
                            ReferenceType.HOMEWORK, homework.getId());
                    sent++;
                }
            }
        }
        if (sent > 0) {
            log.info("Sent {} homework-due-tomorrow reminder(s)", sent);
        }
    }
}
