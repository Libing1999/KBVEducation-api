package com.kbv.education.service.impl;

import com.kbv.education.dto.certificate.CertificateResponse;
import com.kbv.education.entity.Certificate;
import com.kbv.education.entity.CertificateTemplate;
import com.kbv.education.entity.Role;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.CertificateType;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.dto.score.StudentScoreResponse;
import com.kbv.education.mapper.CertificateMapper;
import com.kbv.education.repository.CertificateRepository;
import com.kbv.education.repository.CertificateTemplateRepository;
import com.kbv.education.repository.ParentStudentRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.ScoreEngineService;
import com.kbv.education.service.TierEngineService;
import com.kbv.education.service.pdf.CertificatePdfRenderer;
import com.kbv.education.service.storage.FileStorageService;
import com.kbv.education.service.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers certificate generation (Phase 5 Step 2) with the PDF renderer and
 * file storage mocked out - this test is about the business rules and data
 * assembled around the PDF (student must exist and be a STUDENT, an active
 * template must exist, the render output gets stored and a Certificate row
 * persisted), not PDF byte correctness.
 */
@ExtendWith(MockitoExtension.class)
class CertificateServiceImplTest {

    @Mock private CertificateRepository certificateRepository;
    @Mock private CertificateTemplateRepository certificateTemplateRepository;
    @Mock private CertificateMapper certificateMapper;
    @Mock private UserRepository userRepository;
    @Mock private StudentCohortRepository studentCohortRepository;
    @Mock private ParentStudentRepository parentStudentRepository;
    @Mock private TierEngineService tierEngineService;
    @Mock private ScoreEngineService scoreEngineService;
    @Mock private CertificatePdfRenderer certificatePdfRenderer;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks
    private CertificateServiceImpl certificateService;

    private User student;
    private CertificateTemplate template;

    @BeforeEach
    void setUp() {
        Role studentRole = new Role();
        studentRole.setName(RoleType.STUDENT);

        student = new User();
        student.setId(UUID.randomUUID());
        student.setFirstName("Jane");
        student.setLastName("Doe");
        student.setRole(studentRole);

        template = new CertificateTemplate();
        template.setId(UUID.randomUUID());
        template.setCertificateType(CertificateType.TIER_1);
        template.setBodyTemplate("Congratulations {{studentName}}");
        template.setPrimaryColorHex("#1B3A6B");
        template.setActive(true);
    }

    @Test
    void generatesAndStoresACertificateForAStudent() {
        when(userRepository.findByIdAndDeletedFalse(student.getId())).thenReturn(Optional.of(student));
        when(certificateTemplateRepository.findByCertificateTypeAndActiveTrueAndDeletedFalse(CertificateType.TIER_1))
                .thenReturn(Optional.of(template));
        when(studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(student.getId()))
                .thenReturn(Optional.empty());
        lenient().when(tierEngineService.getDisplayTier(student.getId())).thenReturn("Tier 1");
        lenient().when(scoreEngineService.getCurrent(student.getId())).thenReturn(
                new StudentScoreResponse(UUID.randomUUID(), student.getId(), BigDecimal.valueOf(90),
                        BigDecimal.valueOf(90), BigDecimal.valueOf(90), BigDecimal.valueOf(90),
                        BigDecimal.valueOf(93), null));

        byte[] fakePdf = "fake-pdf-bytes".getBytes();
        when(certificatePdfRenderer.renderTierCertificate(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(fakePdf);

        StoredFile storedFile = new StoredFile("KBV-STORED.pdf", "KBV-STORED.pdf", "application/pdf", fakePdf.length);
        when(fileStorageService.store(any(byte[].class), anyString(), any(), any())).thenReturn(storedFile);

        when(certificateRepository.save(any(Certificate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(certificateMapper.toResponse(any(Certificate.class))).thenReturn(
                new CertificateResponse(UUID.randomUUID(), student.getId(), "Jane Doe",
                        CertificateType.TIER_1, "KBV-2026-T1-ABCDEF12", null, "Tier 1", null, null));

        CertificateResponse response = certificateService.generate(student.getId(), CertificateType.TIER_1);

        ArgumentCaptor<Certificate> savedCertificate = ArgumentCaptor.forClass(Certificate.class);
        verify(certificateRepository).save(savedCertificate.capture());

        assertThat(savedCertificate.getValue().getStudent()).isEqualTo(student);
        assertThat(savedCertificate.getValue().getCertificateType()).isEqualTo(CertificateType.TIER_1);
        assertThat(savedCertificate.getValue().getFilePath()).isEqualTo("certificates/KBV-STORED.pdf");
        assertThat(response.certificateNumber()).isEqualTo("KBV-2026-T1-ABCDEF12");
    }

    @Test
    void completionCertificatesStillUseTheLegacyGenericTemplate() {
        template.setCertificateType(CertificateType.COMPLETION);

        when(userRepository.findByIdAndDeletedFalse(student.getId())).thenReturn(Optional.of(student));
        when(certificateTemplateRepository.findByCertificateTypeAndActiveTrueAndDeletedFalse(CertificateType.COMPLETION))
                .thenReturn(Optional.of(template));
        when(studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(student.getId()))
                .thenReturn(Optional.empty());
        lenient().when(tierEngineService.getDisplayTier(student.getId())).thenReturn("Tier 1");

        byte[] fakePdf = "fake-pdf-bytes".getBytes();
        when(certificatePdfRenderer.render(anyString(), anyMap(), anyString(), anyString(), any(), anyString(),
                anyString(), anyString(), anyString())).thenReturn(fakePdf);

        StoredFile storedFile = new StoredFile("KBV-STORED.pdf", "KBV-STORED.pdf", "application/pdf", fakePdf.length);
        when(fileStorageService.store(any(byte[].class), anyString(), any(), any())).thenReturn(storedFile);

        when(certificateRepository.save(any(Certificate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(certificateMapper.toResponse(any(Certificate.class))).thenReturn(
                new CertificateResponse(UUID.randomUUID(), student.getId(), "Jane Doe",
                        CertificateType.COMPLETION, "KBV-2026-COC-ABCDEF12", null, "Tier 1", null, null));

        certificateService.generate(student.getId(), CertificateType.COMPLETION);

        verify(certificatePdfRenderer).render(anyString(), anyMap(), anyString(), anyString(), any(),
                anyString(), anyString(), anyString(), anyString());
        verify(certificatePdfRenderer, never())
                .renderTierCertificate(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void rejectsGenerationForANonStudentUser() {
        Role adminRole = new Role();
        adminRole.setName(RoleType.SUPER_ADMIN);
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setRole(adminRole);

        when(userRepository.findByIdAndDeletedFalse(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> certificateService.generate(admin.getId(), CertificateType.TIER_1))
                .isInstanceOf(BusinessRuleException.class);
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void throwsWhenStudentDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedFalse(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> certificateService.generate(missingId, CertificateType.TIER_1))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsWhenNoActiveTemplateExistsForTheType() {
        when(userRepository.findByIdAndDeletedFalse(student.getId())).thenReturn(Optional.of(student));
        when(certificateTemplateRepository.findByCertificateTypeAndActiveTrueAndDeletedFalse(CertificateType.TIER_1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> certificateService.generate(student.getId(), CertificateType.TIER_1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No active certificate template");
        verify(certificateRepository, never()).save(any());
    }
}
