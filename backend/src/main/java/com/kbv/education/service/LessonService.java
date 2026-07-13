package com.kbv.education.service;

import com.kbv.education.dto.common.ReorderRequest;
import com.kbv.education.dto.lesson.CreateLessonRequest;
import com.kbv.education.dto.lesson.LessonResponse;
import com.kbv.education.dto.lesson.UpdateLessonRequest;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.LessonStatus;

import java.util.UUID;

/** Admin lesson management (Step 3). */
public interface LessonService {

    PageResponse<LessonResponse> list(UUID cohortId, LessonStatus status, String search,
                                      int page, int size, String sort, String direction);

    LessonResponse get(UUID id);

    LessonResponse create(CreateLessonRequest request);

    LessonResponse update(UUID id, UpdateLessonRequest request);

    void delete(UUID id);

    LessonResponse publish(UUID id);

    LessonResponse unpublish(UUID id);

    LessonResponse duplicate(UUID id);

    void reorder(ReorderRequest request);
}
