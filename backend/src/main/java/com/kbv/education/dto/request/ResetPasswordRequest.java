package com.kbv.education.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin-initiated password reset for a user. */
public record ResetPasswordRequest(
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
