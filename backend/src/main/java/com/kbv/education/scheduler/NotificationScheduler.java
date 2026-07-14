package com.kbv.education.scheduler;

import com.kbv.education.entity.Homework;
import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.Quiz;
import com.kbv.education.entity.enums.NotificationType;
import com.kbv.education.entity.enums.QuizStatus;
import com.kbv.education.entity.enums.ReferenceType;
import com.kbv.education.repository.HomeworkRepository;
import com.kbv.education.repository.HomeworkSubmissionRepository;
import com.kbv.education.repository.QuizAttemptRepository;
import com.kbv.education.repository.QuizRepository;
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
 * Scheduled reminder emitters (in-app only — no push/email).
 * <ul>
 *   <li>Daily: {@code HOMEWORK_DUE_TOMORROW} to students with homework due the next day.</li>
 *   <li>Weekly: {@code QUIZ_REMINDER} to students with a published quiz they haven't completed.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
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

    /**
     * Weekly reminder for published quizzes a student has not yet completed.
     * Runs Monday morning by default to avoid daily spam.
     */
    @Scheduled(cron = "${app.notifications.quiz-reminder-cron:0 0 8 * * MON}")
    @Transactional
    public void sendQuizReminders() {
        List<Quiz> published = quizRepository.findByStatusAndDeletedFalse(QuizStatus.PUBLISHED);
        int sent = 0;
        for (Quiz quiz : published) {
            Lesson lesson = quiz.getLesson();
            if (!lesson.isPublished() || lesson.isDeleted()) {
                continue;
            }
            for (var assignment : studentCohortRepository
                    .findByCohort_IdAndActiveTrueAndDeletedFalse(lesson.getCohort().getId())) {
                UUID studentId = assignment.getStudent().getId();
                if (!quizAttemptRepository.existsByQuiz_IdAndStudent_IdAndDeletedFalse(quiz.getId(), studentId)) {
                    notificationService.notify(studentId, NotificationType.QUIZ_REMINDER,
                            "Quiz Reminder", "You haven't completed the quiz for " + lesson.getTitle(),
                            ReferenceType.QUIZ, quiz.getId());
                    sent++;
                }
            }
        }
        if (sent > 0) {
            log.info("Sent {} quiz reminder(s)", sent);
        }
    }
}
