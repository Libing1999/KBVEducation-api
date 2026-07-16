package com.kbv.education.dto.certificate;

import com.kbv.education.entity.enums.CertificateType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GenerateCertificateRequest(
        @NotNull UUID studentId,
        @NotNull CertificateType certificateType
) {
}
