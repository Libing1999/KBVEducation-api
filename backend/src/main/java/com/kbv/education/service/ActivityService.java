package com.kbv.education.service;

import com.kbv.education.dto.dashboard.ActivityLogResponse;
import com.kbv.education.dto.dashboard.StudyDayResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.ActivityType;
import com.kbv.education.entity.enums.ReferenceType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Records student activity (timeline + day rollup) and serves the timeline and
 * calendar. {@link #record} is best-effort and isolated, so a failure never
 * breaks the core write it accompanies.
 */
public interface ActivityService {

    void record(UUID studentId, ActivityType type, String title, String description,
                ReferenceType referenceType, UUID referenceId, LocalDate date);

    List<ActivityLogResponse> recent(UUID studentId, int limit);

    PageResponse<ActivityLogResponse> list(UUID studentId, int page, int size);

    List<StudyDayResponse> calendar(UUID studentId, LocalDate from, LocalDate to);
}
