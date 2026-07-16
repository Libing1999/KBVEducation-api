package com.kbv.education.dto.certificate;

import com.kbv.education.entity.enums.CertificateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpsertCertificateTemplateRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull CertificateType certificateType,
        @NotBlank String bodyTemplate,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a 6-digit hex color, e.g. #1B3A6B")
        String primaryColorHex,
        @Size(max = 150) String institutionNameOverride,
        String logoPathOverride
) {
}
