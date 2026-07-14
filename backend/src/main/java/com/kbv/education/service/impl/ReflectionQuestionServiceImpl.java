package com.kbv.education.service.impl;

import com.kbv.education.dto.common.ReorderRequest;
import com.kbv.education.dto.reflection.ReflectionQuestionRequest;
import com.kbv.education.dto.reflection.ReflectionQuestionResponse;
import com.kbv.education.entity.ReflectionQuestion;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.ReflectionQuestionRepository;
import com.kbv.education.service.ReflectionQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReflectionQuestionServiceImpl implements ReflectionQuestionService {

    private final ReflectionQuestionRepository questionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ReflectionQuestionResponse> listAll() {
        return questionRepository.findByDeletedFalseOrderByDisplayOrderAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReflectionQuestionResponse> listEnabled() {
        return questionRepository.findByEnabledTrueAndDeletedFalseOrderByDisplayOrderAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ReflectionQuestionResponse create(ReflectionQuestionRequest request) {
        int nextOrder = questionRepository.findFirstByDeletedFalseOrderByDisplayOrderDesc()
                .map(q -> q.getDisplayOrder() + 1)
                .orElse(0);

        ReflectionQuestion q = new ReflectionQuestion();
        q.setQuestionText(request.questionText().trim());
        q.setEnabled(request.enabled() == null || request.enabled());
        q.setDisplayOrder(nextOrder);
        return toResponse(questionRepository.save(q));
    }

    @Override
    @Transactional
    public ReflectionQuestionResponse update(UUID id, ReflectionQuestionRequest request) {
        ReflectionQuestion q = load(id);
        q.setQuestionText(request.questionText().trim());
        if (request.enabled() != null) {
            q.setEnabled(request.enabled());
        }
        return toResponse(questionRepository.save(q));
    }

    @Override
    @Transactional
    public ReflectionQuestionResponse setEnabled(UUID id, boolean enabled) {
        ReflectionQuestion q = load(id);
        q.setEnabled(enabled);
        return toResponse(questionRepository.save(q));
    }

    @Override
    @Transactional
    public void reorder(ReorderRequest request) {
        for (ReorderRequest.Item item : request.items()) {
            questionRepository.findByIdAndDeletedFalse(item.id())
                    .ifPresent(q -> {
                        q.setDisplayOrder(item.displayOrder());
                        questionRepository.save(q);
                    });
        }
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        ReflectionQuestion q = load(id);
        q.setDeleted(true);
        questionRepository.save(q);
    }

    private ReflectionQuestion load(UUID id) {
        return questionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Reflection question", id));
    }

    private ReflectionQuestionResponse toResponse(ReflectionQuestion q) {
        return new ReflectionQuestionResponse(q.getId(), q.getQuestionText(), q.getDisplayOrder(), q.isEnabled());
    }
}
