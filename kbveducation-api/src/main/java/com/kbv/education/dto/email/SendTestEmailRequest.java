package com.kbv.education.dto.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendTestEmailRequest(
        @NotBlank @Email String recipient
) {
}
