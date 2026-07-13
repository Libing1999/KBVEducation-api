package com.kbv.education.service;

import com.kbv.education.dto.request.CreateParentRequest;
import com.kbv.education.dto.request.LinkStudentRequest;
import com.kbv.education.dto.request.UpdateUserRequest;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.response.ParentResponse;
import com.kbv.education.entity.enums.UserStatus;

import java.util.UUID;

public interface ParentService {

    PageResponse<ParentResponse> list(UserStatus status, String search,
                                      int page, int size, String sort, String direction);

    ParentResponse get(UUID id);

    ParentResponse create(CreateParentRequest request);

    ParentResponse update(UUID id, UpdateUserRequest request);

    ParentResponse linkStudent(UUID parentId, LinkStudentRequest request);

    void unlinkStudent(UUID parentId);

    void softDelete(UUID id);
}
