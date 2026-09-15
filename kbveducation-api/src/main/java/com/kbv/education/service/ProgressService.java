package com.kbv.education.service;

import com.kbv.education.dto.dashboard.AdminStatisticsResponse;
import com.kbv.education.dto.dashboard.StudentProgressResponse;

import java.util.UUID;

/** Progress statistics for students/parents and aggregated admin cards. */
public interface ProgressService {

    /**
     * Progress for the authenticated user: a student's own, or one of a parent's linked
     * students. {@code requestedStudentId} selects which child for a multi-child parent;
     * null defaults to their first-linked child. Ignored for a student caller.
     */
    StudentProgressResponse getProgress(UUID userId, UUID requestedStudentId);

    /** Progress for a specific student (admin view). */
    StudentProgressResponse getProgressForStudent(UUID studentId);

    /**
     * Resolve the effective student behind the authenticated user: self for a student,
     * or {@code requestedStudentId} (validated as one of their linked children) — or their
     * first-linked child if null — for a parent.
     */
    UUID resolveStudentId(UUID userId, UUID requestedStudentId);

    AdminStatisticsResponse adminStatistics();
}
