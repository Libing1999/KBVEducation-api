package com.kbv.education.service;

import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.dto.lesson.StudentLessonDetailResponse;
import com.kbv.education.dto.lesson.StudentLessonResponse;
import com.kbv.education.dto.response.PageResponse;

import java.util.Optional;
import java.util.UUID;

/**
 * Student/parent lesson access (Step 9). Resolves the effective student for the
 * caller (a parent sees the linked student's published lessons).
 */
public interface StudentLessonService {

    /**
     * {@code requestedStudentId} selects which child for a multi-child parent caller
     * (validated against their linked children); ignored for a student caller, and
     * defaults to the parent's first-linked child when null.
     */
    PageResponse<StudentLessonResponse> myLessons(UUID userId, UUID requestedStudentId, int page, int size);

    StudentLessonDetailResponse getLessonDetail(UUID userId, UUID requestedStudentId, UUID lessonId);

    /** The published lesson dated today in the caller's (effective student's) active cohort,
     * if any — the "Today's Log" screen uses this to decide whether it's a lesson day (Recall +
     * Post-Lesson Homework appear) or not (Reflection + Practice only). Empty when the student
     * has no active cohort or no lesson is scheduled for today. */
    Optional<StudentLessonDetailResponse> getTodayLesson(UUID userId, UUID requestedStudentId);

    /** Download a file that belongs to a lesson the caller may access. */
    FileDownloadResult downloadLessonFile(UUID userId, UUID requestedStudentId, UUID lessonId, UUID fileId);
}
