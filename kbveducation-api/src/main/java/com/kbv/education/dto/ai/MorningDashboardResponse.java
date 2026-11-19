package com.kbv.education.dto.ai;

import com.kbv.education.entity.enums.AiJobStatus;
import com.kbv.education.entity.enums.ReflectionType;
import com.kbv.education.entity.enums.StudyType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The morning section of the admin dashboard (memo 12.10.2026, 4.5), for one day
 * (yesterday in the AI time zone by default). The numbers come from the app's own data and
 * are always complete; AI parts show "being analysed" while their status is QUEUED or SUBMITTED.
 */
public record MorningDashboardResponse(
        LocalDate date,
        String timezone,
        Numbers numbers,
        List<SuspiciousSession> suspiciousSessions,
        List<StudentReflections> reflections,
        List<NeedsMe> needsMe
) {

    public record Student(UUID id, String name) {
    }

    public record Numbers(int totalStudents, int studentsWithPractice, int totalSessions,
                          int studentsWithReflection, List<Student> studentsWithNothing) {
    }

    /** A session with a flag or a red rating, lowest rating first. */
    public record SuspiciousSession(UUID sessionId, Student student, String subject, StudyType studyType,
                                    AiJobStatus aiStatus, Integer trustRating, Integer flagCount, String summary) {
    }

    public record ReflectionItem(UUID reflectionId, ReflectionType type, AiJobStatus aiStatus, Integer flagCount,
                                 boolean needsAttention, String summary, boolean hasAudio) {
    }

    /** All of one student's reflections for the day; groups with a flag come first. */
    public record StudentReflections(Student student, List<ReflectionItem> items) {
    }

    /** A student who needs something from the admin, with a one-line reason for each thing. */
    public record NeedsMe(Student student, List<String> reasons) {
    }
}
