package com.kbv.education.service;

import com.kbv.education.dto.homework.HomeworkRequest;
import com.kbv.education.dto.homework.HomeworkResponse;

import java.util.UUID;

/** Admin homework configuration per lesson (Step 5). */
public interface HomeworkService {

    /** The homework configured for a lesson, or throws if none exists. */
    HomeworkResponse getByLesson(UUID lessonId);

    HomeworkResponse createOrUpdateForLesson(UUID lessonId, HomeworkRequest request);

    void delete(UUID homeworkId);
}
