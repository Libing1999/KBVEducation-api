package com.kbv.education.service;

import com.kbv.education.dto.request.CreateUserRequest;
import com.kbv.education.dto.request.ResetPasswordRequest;
import com.kbv.education.dto.request.UpdateStatusRequest;
import com.kbv.education.dto.request.UpdateUserRequest;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.response.UserResponse;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;

import java.util.UUID;

public interface UserService {

    PageResponse<UserResponse> list(RoleType role, UserStatus status, String search,
                                    int page, int size, String sort, String direction);

    UserResponse get(UUID id);

    UserResponse create(CreateUserRequest request);

    UserResponse update(UUID id, UpdateUserRequest request);

    UserResponse updateStatus(UUID id, UpdateStatusRequest request);

    void resetPassword(UUID id, ResetPasswordRequest request);

    void softDelete(UUID id);
}
