package com.kbv.education.service.impl;

import com.kbv.education.audit.Audited;
import com.kbv.education.dto.certificate.CertificateResponse;
import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.entity.Certificate;
import com.kbv.education.entity.CertificateTemplate;
import com.kbv.education.entity.StudentCohort;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.CertificateType;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.CertificateMapper;
import com.kbv.education.repository.CertificateRepository;
import com.kbv.education.repository.CertificateTemplateRepository;
import com.kbv.education.repository.ParentStudentRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.CertificateService;
import com.kbv.education.service.TierEngineService;
import com.kbv.education.service.pdf.CertificatePdfRenderer;
import com.kbv.education.service.storage.FileStorageService;
import com.kbv.education.service.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateServiceImpl implements CertificateService {

    private static final String STORAGE_SUBDIR = "certificates";
    private static final String DEFAULT_INSTITUTION_NAME = "KBV Education";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private final CertificateRepository certificateRepository;
    private final CertificateTemplateRepository certificateTemplateRepository;
    private final CertificateMapper certificateMapper;
    private final UserRepository userRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final TierEngineService tierEngineService;
    private final CertificatePdfRenderer certificatePdfRenderer;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    @Audited(action = "CERTIFICATE_GENERATED", entityType = "CERTIFICATE")
    public CertificateResponse generate(UUID studentId, CertificateType certificateType) {
        User student = userRepository.findByIdAndDeletedFalse(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
        if (student.getRole().getName() != RoleType.STUDENT) {
            throw new BusinessRuleException("Certificates can only be generated for students");
        }

        CertificateTemplate template = certificateTemplateRepository
                .findByCertificateTypeAndActiveTrueAndDeletedFalse(certificateType)
                .orElseThrow(() -> new BusinessRuleException(
                        "No active certificate template for type " + certificateType));

        StudentCohort membership = studentCohortRepository
                .findByStudent_IdAndActiveTrueAndDeletedFalse(studentId).orElse(null);
        String tierAtIssue = tierEngineService.getDisplayTier(studentId);

        String certificateNumber = generateCertificateNumber(certificateType);
        String institutionName = template.getInstitutionNameOverride() != null
                ? template.getInstitutionNameOverride() : DEFAULT_INSTITUTION_NAME;
        String issueDate = LocalDate.now().format(DATE_FORMAT);

        Map<String, String> placeholders = Map.of(
                "studentName", student.getFullName(),
                "tierName", tierAtIssue == null ? "" : tierAtIssue,
                "cohortName", membership == null ? "" : membership.getCohort().getName(),
                "issueDate", issueDate,
                "certificateNumber", certificateNumber);

        byte[] pdf = certificatePdfRenderer.render(
                template.getBodyTemplate(), placeholders, CertificateTitles.of(certificateType),
                institutionName, template.getLogoPathOverride(), template.getPrimaryColorHex(),
                student.getFullName(), certificateNumber, issueDate);

        StoredFile stored = fileStorageService.store(
                pdf, certificateNumber + ".pdf", "application/pdf", STORAGE_SUBDIR);

        Certificate certificate = new Certificate();
        certificate.setStudent(student);
        certificate.setTemplate(template);
        certificate.setCertificateType(certificateType);
        certificate.setCertificateNumber(certificateNumber);
        certificate.setCohort(membership == null ? null : membership.getCohort());
        certificate.setTierAtIssue(tierAtIssue);
        certificate.setFilePath(STORAGE_SUBDIR + "/" + stored.storedName());
        certificate.setInstitutionNameSnapshot(institutionName);
        certificate.setLogoPathSnapshot(template.getLogoPathOverride());
        certificate.setPrimaryColorSnapshot(template.getPrimaryColorHex());

        Certificate saved = certificateRepository.save(certificate);
        log.info("Generated certificate {} ({}) for student {}", saved.getId(), certificateType, studentId);
        return certificateMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateResponse> listForAdmin() {
        return certificateRepository.findByDeletedFalseOrderByCreatedAtDesc().stream()
                .map(certificateMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateResponse> listForStudent(UUID studentId) {
        return certificateRepository.findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(studentId).stream()
                .map(certificateMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateResponse> listForParent(UUID parentId) {
        return listForStudent(resolveLinkedStudentId(parentId));
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult downloadForAdmin(UUID certificateId) {
        return buildDownload(getCertificate(certificateId));
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult downloadForStudent(UUID studentId, UUID certificateId) {
        Certificate certificate = getCertificate(certificateId);
        if (!certificate.getStudent().getId().equals(studentId)) {
            throw ResourceNotFoundException.of("Certificate", certificateId);
        }
        return buildDownload(certificate);
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult downloadForParent(UUID parentId, UUID certificateId) {
        UUID linkedStudentId = resolveLinkedStudentId(parentId);
        return downloadForStudent(linkedStudentId, certificateId);
    }

    private UUID resolveLinkedStudentId(UUID parentId) {
        return parentStudentRepository.findByParent_IdAndDeletedFalse(parentId)
                .map(ps -> ps.getStudent().getId())
                .orElseThrow(() -> new BusinessRuleException("No student is linked to this parent account"));
    }

    private Certificate getCertificate(UUID certificateId) {
        return certificateRepository.findByIdAndDeletedFalse(certificateId)
                .orElseThrow(() -> ResourceNotFoundException.of("Certificate", certificateId));
    }

    private FileDownloadResult buildDownload(Certificate certificate) {
        Resource resource = fileStorageService.loadAsResource(STORAGE_SUBDIR,
                certificate.getFilePath().substring(STORAGE_SUBDIR.length() + 1));
        String fileName = certificate.getCertificateNumber() + ".pdf";
        long size;
        try {
            size = resource.contentLength();
        } catch (Exception e) {
            size = 0;
        }
        return new FileDownloadResult(fileName, "application/pdf", size, resource);
    }

    private String generateCertificateNumber(CertificateType type) {
        String typeCode = switch (type) {
            case TIER_1 -> "T1";
            case TIER_2 -> "T2";
            case TIER_3 -> "T3";
            case COMPLETION -> "COC";
        };
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "KBV-%d-%s-%s".formatted(Year.now().getValue(), typeCode, suffix);
    }
}
