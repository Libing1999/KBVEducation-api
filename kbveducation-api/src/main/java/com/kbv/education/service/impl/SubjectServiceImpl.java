package com.kbv.education.service.impl;

import com.kbv.education.audit.Audited;
import com.kbv.education.dto.common.ReorderRequest;
import com.kbv.education.dto.subject.SubjectRequest;
import com.kbv.education.dto.subject.SubjectResponse;
import com.kbv.education.entity.Subject;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.PracticeSessionRepository;
import com.kbv.education.repository.SubjectRepository;
import com.kbv.education.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;
    private final PracticeSessionRepository practiceSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponse> listAll() {
        return subjectRepository.findByDeletedFalseOrderByDisplayOrderAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponse> listEnabled() {
        return subjectRepository.findByEnabledTrueAndDeletedFalseOrderByDisplayOrderAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SubjectResponse create(SubjectRequest request) {
        String name = request.name().trim();
        if (subjectRepository.existsByNameIgnoreCaseAndDeletedFalse(name)) {
            throw new BadRequestException("A subject named '" + name + "' already exists");
        }

        int nextOrder = subjectRepository.findFirstByDeletedFalseOrderByDisplayOrderDesc()
                .map(s -> s.getDisplayOrder() + 1)
                .orElse(0);

        Subject subject = new Subject();
        subject.setName(name);
        subject.setEnabled(request.enabled() == null || request.enabled());
        subject.setDisplayOrder(nextOrder);
        return toResponse(subjectRepository.save(subject));
    }

    @Override
    @Transactional
    @Audited(action = "SUBJECT_EDITED", entityType = "SUBJECT")
    public SubjectResponse update(UUID id, SubjectRequest request) {
        Subject subject = load(id);
        String name = request.name().trim();
        if (subjectRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(name, id)) {
            throw new BadRequestException("A subject named '" + name + "' already exists");
        }
        subject.setName(name);
        if (request.enabled() != null) {
            subject.setEnabled(request.enabled());
        }
        return toResponse(subjectRepository.save(subject));
    }

    @Override
    @Transactional
    public SubjectResponse setEnabled(UUID id, boolean enabled) {
        Subject subject = load(id);
        subject.setEnabled(enabled);
        return toResponse(subjectRepository.save(subject));
    }

    @Override
    @Transactional
    public void reorder(ReorderRequest request) {
        for (ReorderRequest.Item item : request.items()) {
            subjectRepository.findByIdAndDeletedFalse(item.id())
                    .ifPresent(s -> {
                        s.setDisplayOrder(item.displayOrder());
                        subjectRepository.save(s);
                    });
        }
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Subject subject = load(id);
        if (practiceSessionRepository.existsBySubjectIgnoreCaseAndDeletedFalse(subject.getName())) {
            throw new BusinessRuleException(
                    "Cannot delete '" + subject.getName() + "' — it is used by one or more practice sessions");
        }
        subject.setDeleted(true);
        subjectRepository.save(subject);
    }

    private Subject load(UUID id) {
        return subjectRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Subject", id));
    }

    private SubjectResponse toResponse(Subject s) {
        return new SubjectResponse(s.getId(), s.getName(), s.getDisplayOrder(), s.isEnabled());
    }
}
