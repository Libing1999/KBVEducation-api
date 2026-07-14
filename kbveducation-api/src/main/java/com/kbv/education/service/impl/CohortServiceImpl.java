package com.kbv.education.service.impl;

import com.kbv.education.dto.request.AssignCohortRequest;
import com.kbv.education.dto.request.CreateCohortRequest;
import com.kbv.education.dto.request.UpdateCohortRequest;
import com.kbv.education.dto.response.CohortResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.response.StudentResponse;
import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.enums.CohortStatus;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.CohortMapper;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.service.CohortService;
import com.kbv.education.service.StudentResponseAssembler;
import com.kbv.education.service.StudentService;
import com.kbv.education.repository.spec.CohortSpecifications;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CohortServiceImpl implements CohortService {

    private static final List<String> SORTABLE =
            List.of("createdAt", "name", "startDate", "endDate", "examDate", "status", "maxStudents");

    private final CohortRepository cohortRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final CohortMapper cohortMapper;
    private final StudentResponseAssembler studentAssembler;
    private final StudentService studentService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CohortResponse> list(CohortStatus status, String search,
                                             int page, int size, String sort, String direction) {
        Specification<Cohort> spec = Specification.where(CohortSpecifications.notDeleted())
                .and(CohortSpecifications.hasStatus(status))
                .and(CohortSpecifications.search(search));

        Pageable pageable = PageableBuilder.build(page, size, sort, direction, SORTABLE);
        Page<Cohort> result = cohortRepository.findAll(spec, pageable);
        return PageResponse.from(result, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CohortResponse get(UUID id) {
        return toResponse(getCohort(id));
    }

    @Override
    @Transactional
    public CohortResponse create(CreateCohortRequest request) {
        validateDates(request.startDate(), request.endDate());

        Cohort cohort = new Cohort();
        cohort.setName(request.name());
        cohort.setDescription(request.description());
        cohort.setStartDate(request.startDate());
        cohort.setEndDate(request.endDate());
        cohort.setExamDate(request.examDate());
        cohort.setStatus(request.status() != null ? request.status() : CohortStatus.UPCOMING);
        cohort.setMaxStudents(request.maxStudents());

        Cohort saved = cohortRepository.save(cohort);
        log.info("Created cohort {}", saved.getName());
        return cohortMapper.toResponse(saved, 0L);
    }

    @Override
    @Transactional
    public CohortResponse update(UUID id, UpdateCohortRequest request) {
        validateDates(request.startDate(), request.endDate());

        Cohort cohort = getCohort(id);
        cohort.setName(request.name());
        cohort.setDescription(request.description());
        cohort.setStartDate(request.startDate());
        cohort.setEndDate(request.endDate());
        cohort.setExamDate(request.examDate());
        cohort.setStatus(request.status());
        cohort.setMaxStudents(request.maxStudents());
        return toResponse(cohortRepository.save(cohort));
    }

    @Override
    @Transactional
    public void archive(UUID id) {
        Cohort cohort = getCohort(id);
        // Free up active assignments so student capacity/counts stay accurate.
        studentCohortRepository.findByCohort_IdAndActiveTrueAndDeletedFalse(id)
                .forEach(sc -> {
                    sc.setActive(false);
                    studentCohortRepository.save(sc);
                });
        cohort.setStatus(CohortStatus.ARCHIVED);
        cohort.setDeleted(true);
        cohortRepository.save(cohort);
        log.info("Archived cohort {}", cohort.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentResponse> listStudents(UUID cohortId) {
        getCohort(cohortId);
        return studentCohortRepository.findByCohort_IdAndActiveTrueAndDeletedFalse(cohortId).stream()
                .map(sc -> studentAssembler.toResponse(sc.getStudent()))
                .toList();
    }

    @Override
    @Transactional
    public CohortResponse assignStudent(UUID cohortId, UUID studentId) {
        Cohort cohort = getCohort(cohortId);
        // Reuse the student assignment logic (capacity + single-active-cohort rules).
        studentService.assignCohort(studentId, new AssignCohortRequest(cohortId));
        return toResponse(cohort);
    }

    @Override
    @Transactional
    public void removeStudent(UUID cohortId, UUID studentId) {
        getCohort(cohortId);
        studentCohortRepository.findByStudent_IdAndCohort_IdAndDeletedFalse(studentId, cohortId)
                .filter(com.kbv.education.entity.StudentCohort::isActive)
                .ifPresentOrElse(sc -> {
                    sc.setActive(false);
                    studentCohortRepository.save(sc);
                    log.info("Removed student {} from cohort {}", studentId, cohortId);
                }, () -> {
                    throw new ResourceNotFoundException(
                            "Student " + studentId + " is not actively assigned to cohort " + cohortId);
                });
    }

    private CohortResponse toResponse(Cohort cohort) {
        long count = studentCohortRepository.countByCohort_IdAndActiveTrueAndDeletedFalse(cohort.getId());
        return cohortMapper.toResponse(cohort, count);
    }

    private void validateDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessRuleException("End date must be on or after the start date");
        }
    }

    private Cohort getCohort(UUID id) {
        return cohortRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Cohort", id));
    }
}
