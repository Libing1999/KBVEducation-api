package com.kbv.education.service;

import com.kbv.education.dto.common.ReorderRequest;
import com.kbv.education.dto.reflection.ReflectionQuestionRequest;
import com.kbv.education.dto.reflection.ReflectionQuestionResponse;

import java.util.List;
import java.util.UUID;

/** Admin configuration of the daily reflection questions (never hardcoded). */
public interface ReflectionQuestionService {

    List<ReflectionQuestionResponse> listAll();

    List<ReflectionQuestionResponse> listEnabled();

    ReflectionQuestionResponse create(ReflectionQuestionRequest request);

    ReflectionQuestionResponse update(UUID id, ReflectionQuestionRequest request);

    ReflectionQuestionResponse setEnabled(UUID id, boolean enabled);

    void reorder(ReorderRequest request);

    void delete(UUID id);
}
