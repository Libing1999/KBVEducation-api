package com.kbv.education.service;

import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.dto.lesson.StudentLessonDetailResponse;
import com.kbv.education.dto.lesson.StudentLessonResponse;
import com.kbv.education.dto.response.PageResponse;

import java.util.UUID;

/**
 * Student/parent lesson access (Step 9). Resolves the effective student for the
 * caller (a parent sees the linked student's published lessons).
 */
public interface StudentLessonService {

    PageResponse<StudentLessonResponse> myLessons(UUID userId, int page, int size);

    StudentLessonDetailResponse getLessonDetail(UUID userId, UUID lessonId);

    /** Download a file that belongs to a lesson the caller may access. */
    FileDownloadResult downloadLessonFile(UUID userId, UUID lessonId, UUID fileId);
}
