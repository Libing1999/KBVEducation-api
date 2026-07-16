package com.kbv.education.dto.certificate;

import com.kbv.education.entity.enums.CertificateType;

import java.util.UUID;

public record CertificateTemplateResponse(
        UUID id,
        String name,
        CertificateType certificateType,
        String bodyTemplate,
        String primaryColorHex,
        String institutionNameOverride,
        String logoPathOverride,
        boolean active
) {
}
