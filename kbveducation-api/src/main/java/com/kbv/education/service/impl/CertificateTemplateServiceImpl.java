package com.kbv.education.service.impl;

import com.kbv.education.dto.certificate.CertificateTemplateResponse;
import com.kbv.education.dto.certificate.UpsertCertificateTemplateRequest;
import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.entity.CertificateTemplate;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.CertificateTemplateMapper;
import com.kbv.education.repository.CertificateTemplateRepository;
import com.kbv.education.service.CertificateTemplateService;
import com.kbv.education.service.pdf.CertificatePdfRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateTemplateServiceImpl implements CertificateTemplateService {

    private static final String DEFAULT_INSTITUTION_NAME = "KBV Education";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private final CertificateTemplateRepository certificateTemplateRepository;
    private final CertificateTemplateMapper certificateTemplateMapper;
    private final CertificatePdfRenderer certificatePdfRenderer;

    @Override
    @Transactional(readOnly = true)
    public List<CertificateTemplateResponse> list() {
        return certificateTemplateRepository.findByDeletedFalseOrderByCertificateTypeAscNameAsc().stream()
                .map(certificateTemplateMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CertificateTemplateResponse create(UpsertCertificateTemplateRequest request) {
        CertificateTemplate template = new CertificateTemplate();
        applyRequest(template, request);
        CertificateTemplate saved = certificateTemplateRepository.save(template);
        log.info("Created certificate template {} ({})", saved.getId(), saved.getCertificateType());
        return certificateTemplateMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CertificateTemplateResponse update(UUID id, UpsertCertificateTemplateRequest request) {
        CertificateTemplate template = getTemplate(id);
        applyRequest(template, request);
        CertificateTemplate saved = certificateTemplateRepository.save(template);
        return certificateTemplateMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CertificateTemplateResponse activate(UUID id) {
        CertificateTemplate template = getTemplate(id);

        certificateTemplateRepository
                .findByCertificateTypeAndActiveTrueAndDeletedFalseAndIdNot(template.getCertificateType(), id)
                .forEach(other -> other.setActive(false));

        template.setActive(true);
        CertificateTemplate saved = certificateTemplateRepository.save(template);
        log.info("Activated certificate template {} for type {}", saved.getId(), saved.getCertificateType());
        return certificateTemplateMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult preview(UUID id) {
        CertificateTemplate template = getTemplate(id);

        Map<String, String> sample = Map.of(
                "studentName", "Jordan Sample",
                "tierName", "Tier 1",
                "cohortName", "Sample Cohort",
                "issueDate", LocalDate.now().format(DATE_FORMAT),
                "certificateNumber", "PREVIEW");

        String institutionName = template.getInstitutionNameOverride() != null
                ? template.getInstitutionNameOverride() : DEFAULT_INSTITUTION_NAME;

        byte[] pdf = certificatePdfRenderer.render(
                template.getBodyTemplate(), sample, CertificateTitles.of(template.getCertificateType()),
                institutionName, template.getLogoPathOverride(), template.getPrimaryColorHex(),
                sample.get("studentName"), sample.get("certificateNumber"), sample.get("issueDate"));

        return new FileDownloadResult("certificate-preview.pdf", "application/pdf", pdf.length,
                new ByteArrayResource(pdf));
    }

    private void applyRequest(CertificateTemplate template, UpsertCertificateTemplateRequest request) {
        template.setName(request.name());
        template.setCertificateType(request.certificateType());
        template.setBodyTemplate(request.bodyTemplate());
        template.setPrimaryColorHex(request.primaryColorHex());
        template.setInstitutionNameOverride(request.institutionNameOverride());
        template.setLogoPathOverride(request.logoPathOverride());
    }

    private CertificateTemplate getTemplate(UUID id) {
        return certificateTemplateRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Certificate template", id));
    }
}
