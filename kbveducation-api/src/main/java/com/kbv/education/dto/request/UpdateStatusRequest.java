package com.kbv.education.dto.request;

import com.kbv.education.entity.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

/** Activate/deactivate a user. */
public record UpdateStatusRequest(
        @NotNull UserStatus status
) {
}
