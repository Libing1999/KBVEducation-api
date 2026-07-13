package com.kbv.education.service;

import com.kbv.education.dto.request.AssignCohortRequest;
import com.kbv.education.dto.request.CreateStudentRequest;
import com.kbv.education.dto.request.UpdateUserRequest;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.response.StudentResponse;
import com.kbv.education.entity.enums.UserStatus;

import java.util.UUID;

public interface StudentService {

    PageResponse<StudentResponse> list(UserStatus status, String search,
                                       int page, int size, String sort, String direction);

    StudentResponse get(UUID id);

    StudentResponse create(CreateStudentRequest request);

    StudentResponse update(UUID id, UpdateUserRequest request);

    StudentResponse assignCohort(UUID studentId, AssignCohortRequest request);

    void removeFromCohort(UUID studentId);

    void softDelete(UUID id);
}
