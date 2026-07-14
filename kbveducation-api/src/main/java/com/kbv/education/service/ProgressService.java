package com.kbv.education.service;

import com.kbv.education.dto.dashboard.AdminStatisticsResponse;
import com.kbv.education.dto.dashboard.StudentProgressResponse;

import java.util.UUID;

/** Progress statistics for students/parents and aggregated admin cards. */
public interface ProgressService {

    /** Progress for the authenticated user (a student's own, or a parent's linked student). */
    StudentProgressResponse getProgress(UUID userId);

    /** Progress for a specific student (admin view). */
    StudentProgressResponse getProgressForStudent(UUID studentId);

    /** Resolve the student behind the authenticated user (self, or parent's linked student). */
    UUID resolveStudentId(UUID userId);

    AdminStatisticsResponse adminStatistics();
}
