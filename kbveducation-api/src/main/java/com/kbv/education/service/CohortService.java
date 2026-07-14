package com.kbv.education.service;

import com.kbv.education.dto.request.CreateCohortRequest;
import com.kbv.education.dto.request.UpdateCohortRequest;
import com.kbv.education.dto.response.CohortResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.response.StudentResponse;
import com.kbv.education.entity.enums.CohortStatus;

import java.util.List;
import java.util.UUID;

public interface CohortService {

    PageResponse<CohortResponse> list(CohortStatus status, String search,
                                      int page, int size, String sort, String direction);

    CohortResponse get(UUID id);

    CohortResponse create(CreateCohortRequest request);

    CohortResponse update(UUID id, UpdateCohortRequest request);

    /** Archive (soft) a cohort. */
    void archive(UUID id);

    List<StudentResponse> listStudents(UUID cohortId);

    CohortResponse assignStudent(UUID cohortId, UUID studentId);

    void removeStudent(UUID cohortId, UUID studentId);
}
