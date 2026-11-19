package com.kbv.education.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create or replace one file of the reference pack (makes a new version). */
public record SaveReferencePackFileRequest(
        @NotBlank @Size(max = 200) String fileName,
        @NotNull @Size(max = 200_000) String content,
        @Size(max = 500) String note
) {
}
