package com.kbv.education.service;

import com.kbv.education.dto.certificate.CertificateResponse;
import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.entity.enums.CertificateType;

import java.util.List;
import java.util.UUID;

public interface CertificateService {

    /** Resolves the active template for the requested type, renders and stores the PDF, and persists the record. */
    CertificateResponse generate(UUID studentId, CertificateType certificateType);

    List<CertificateResponse> listForAdmin();

    /** The authenticated student's own certificates. */
    List<CertificateResponse> listForStudent(UUID studentId);

    /** A parent's linked student's certificates. */
    List<CertificateResponse> listForParent(UUID parentId);

    /** Any admin may download any certificate. */
    FileDownloadResult downloadForAdmin(UUID certificateId);

    /** A student may only download their own certificate. */
    FileDownloadResult downloadForStudent(UUID studentId, UUID certificateId);

    /** A parent may only download their linked student's certificate. */
    FileDownloadResult downloadForParent(UUID parentId, UUID certificateId);
}
