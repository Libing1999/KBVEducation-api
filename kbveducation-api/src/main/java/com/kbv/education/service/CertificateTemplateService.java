package com.kbv.education.service;

import com.kbv.education.dto.certificate.CertificateTemplateResponse;
import com.kbv.education.dto.certificate.UpsertCertificateTemplateRequest;
import com.kbv.education.dto.file.FileDownloadResult;

import java.util.List;
import java.util.UUID;

public interface CertificateTemplateService {

    List<CertificateTemplateResponse> list();

    CertificateTemplateResponse create(UpsertCertificateTemplateRequest request);

    CertificateTemplateResponse update(UUID id, UpsertCertificateTemplateRequest request);

    /** Activates this template and deactivates any other template of the same type. */
    CertificateTemplateResponse activate(UUID id);

    /** Renders a PDF from synthetic sample data without persisting anything. */
    FileDownloadResult preview(UUID id);
}
