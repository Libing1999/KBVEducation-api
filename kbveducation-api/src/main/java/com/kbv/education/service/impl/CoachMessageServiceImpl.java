package com.kbv.education.service.impl;

import com.kbv.education.dto.message.CoachMessageResponse;
import com.kbv.education.dto.message.ParentMessageResponse;
import com.kbv.education.dto.message.SendMessageRequest;
import com.kbv.education.dto.message.StudentMessageResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.CoachMessage;
import com.kbv.education.entity.CoachMessageRead;
import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.StudentCohort;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.MessageTargetType;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.CoachMessageReadRepository;
import com.kbv.education.repository.CoachMessageRepository;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.repository.ParentStudentRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.CoachMessageService;
import com.kbv.education.utils.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The one manual messaging channel — staff-composed notes, individual or
 * cohort-collective, surfaced read-only to students (Live Action drawer) and
 * parents (Messages from Bhavya card). See {@code Notification}/{@code
 * NotificationServiceImpl} for the separate, system-generated notification
 * feed; this is intentionally a different table/flow since a message here
 * needs collective (fan-out-free) targeting and independent read state per
 * viewer role (a parent's read state must never affect the student's, and
 * vice versa).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachMessageServiceImpl implements CoachMessageService {

    private static final int PARENT_MESSAGE_LIMIT = 10;

    private final CoachMessageRepository coachMessageRepository;
    private final CoachMessageReadRepository coachMessageReadRepository;
    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final ParentStudentRepository parentStudentRepository;

    @Override
    @Transactional
    public CoachMessageResponse send(UUID senderId, SendMessageRequest request) {
        User sender = userRepository.findByIdAndDeletedFalse(senderId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", senderId));

        CoachMessage message = new CoachMessage();
        message.setSender(sender);
        message.setTargetType(request.targetType());
        message.setTag(InputSanitizer.sanitize(request.tag(), 60));
        message.setBody(InputSanitizer.sanitize(request.body(), 2000));

        if (request.targetType() == MessageTargetType.INDIVIDUAL) {
            if (request.studentId() == null || request.cohortId() != null) {
                throw new BusinessRuleException("An individual message needs exactly a studentId (no cohortId)");
            }
            User student = userRepository.findByIdAndDeletedFalse(request.studentId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Student", request.studentId()));
            message.setTargetStudent(student);
        } else {
            if (request.cohortId() == null || request.studentId() != null) {
                throw new BusinessRuleException("A collective message needs exactly a cohortId (no studentId)");
            }
            Cohort cohort = cohortRepository.findByIdAndDeletedFalse(request.cohortId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Cohort", request.cohortId()));
            message.setTargetCohort(cohort);
        }

        CoachMessage saved = coachMessageRepository.save(message);
        log.info("Coach message {} sent by {} ({} -> {})", saved.getId(), senderId, request.targetType(),
                request.targetType() == MessageTargetType.INDIVIDUAL ? request.studentId() : request.cohortId());
        return toAdminResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CoachMessageResponse> adminList(int page, int size) {
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CoachMessage> result = coachMessageRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable);
        return PageResponse.from(result, this::toAdminResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentMessageResponse> listForStudent(UUID studentId) {
        List<CoachMessage> messages = messagesForStudent(studentId);
        Set<UUID> readIds = coachMessageReadRepository.findReadMessageIds(
                studentId, messages.stream().map(CoachMessage::getId).toList());

        return messages.stream()
                .map(m -> new StudentMessageResponse(
                        m.getId(), m.getTargetType(), m.getTag(), m.getBody(), m.getCreatedAt(),
                        readIds.contains(m.getId())))
                .toList();
    }

    @Override
    @Transactional
    public void markReadForStudent(UUID studentId, UUID messageId) {
        CoachMessage message = coachMessageRepository.findById(messageId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));

        if (!isAddressedToStudent(message, studentId)) {
            // Deliberately the same 404 as "doesn't exist" — a student must not be able
            // to distinguish "not mine" from "no such message".
            throw ResourceNotFoundException.of("Message", messageId);
        }
        markRead(message, studentId);
    }

    @Override
    @Transactional
    public List<ParentMessageResponse> listForParent(UUID parentUserId, UUID requestedStudentId) {
        List<com.kbv.education.entity.ParentStudent> links =
                parentStudentRepository.findAllByParent_IdAndDeletedFalseOrderByCreatedAtAsc(parentUserId);
        if (links.isEmpty()) {
            throw new BusinessRuleException("No student is linked to this parent account");
        }
        User student = requestedStudentId == null
                ? links.get(0).getStudent()
                : links.stream()
                        .map(com.kbv.education.entity.ParentStudent::getStudent)
                        .filter(s -> s.getId().equals(requestedStudentId))
                        .findFirst()
                        .orElseThrow(() -> new BusinessRuleException("This student is not linked to your account"));

        List<CoachMessage> messages = messagesForStudent(student.getId()).stream()
                .limit(PARENT_MESSAGE_LIMIT)
                .toList();

        // The parent card is always visible (not behind an open/close gesture like the
        // student's Live Action drawer), so a fetch is the "view" moment — mark read
        // for this parent (and only this parent) as we serve it.
        messages.forEach(m -> markRead(m, parentUserId));

        return messages.stream()
                .map(m -> new ParentMessageResponse(m.getBody(), m.getCreatedAt()))
                .toList();
    }

    // --- helpers -------------------------------------------------------------

    private List<CoachMessage> messagesForStudent(UUID studentId) {
        List<CoachMessage> individual =
                coachMessageRepository.findByTargetStudent_IdAndDeletedFalseOrderByCreatedAtDesc(studentId);

        Optional<StudentCohort> activeCohort =
                studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(studentId);
        List<CoachMessage> collective = activeCohort
                .map(sc -> coachMessageRepository
                        .findByTargetCohort_IdAndDeletedFalseOrderByCreatedAtDesc(sc.getCohort().getId()))
                .orElseGet(List::of);

        List<CoachMessage> merged = new ArrayList<>(individual.size() + collective.size());
        merged.addAll(individual);
        merged.addAll(collective);
        merged.sort(Comparator.comparing(CoachMessage::getCreatedAt).reversed());
        return merged;
    }

    private boolean isAddressedToStudent(CoachMessage message, UUID studentId) {
        if (message.getTargetType() == MessageTargetType.INDIVIDUAL) {
            return message.getTargetStudent() != null && message.getTargetStudent().getId().equals(studentId);
        }
        return message.getTargetCohort() != null && studentCohortRepository
                .findByStudent_IdAndCohort_IdAndDeletedFalse(studentId, message.getTargetCohort().getId())
                .map(StudentCohort::isActive)
                .orElse(false);
    }

    private void markRead(CoachMessage message, UUID readerId) {
        boolean already = coachMessageReadRepository
                .findByMessage_IdAndReader_IdAndDeletedFalse(message.getId(), readerId)
                .isPresent();
        if (already) {
            return;
        }
        CoachMessageRead read = new CoachMessageRead();
        read.setMessage(message);
        read.setReader(userRepository.getReferenceById(readerId));
        coachMessageReadRepository.save(read);
    }

    private CoachMessageResponse toAdminResponse(CoachMessage m) {
        return new CoachMessageResponse(
                m.getId(),
                m.getTargetType(),
                m.getTargetStudent() != null ? m.getTargetStudent().getId() : null,
                m.getTargetStudent() != null ? m.getTargetStudent().getFullName() : null,
                m.getTargetCohort() != null ? m.getTargetCohort().getId() : null,
                m.getTargetCohort() != null ? m.getTargetCohort().getName() : null,
                m.getSender().getFullName(),
                m.getTag(),
                m.getBody(),
                m.getCreatedAt());
    }
}
