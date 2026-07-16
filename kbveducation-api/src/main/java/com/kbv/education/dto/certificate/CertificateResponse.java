package com.kbv.education.dto.certificate;

import com.kbv.education.entity.enums.CertificateStatus;
import com.kbv.education.entity.enums.CertificateType;

import java.time.Instant;
import java.util.UUID;

public record CertificateResponse(
        UUID id,
        UUID studentId,
        String studentName,
        CertificateType certificateType,
        String certificateNumber,
        String cohortName,
        String tierAtIssue,
        CertificateStatus status,
        Instant issuedAt
) {
}
