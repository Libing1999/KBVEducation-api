package com.kbv.education.dto.quiz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OptionRequest(
        @NotBlank @Size(max = 500) String optionText,
        boolean correct
) {
}
