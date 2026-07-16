package com.kbv.education.repository;

import com.kbv.education.entity.CertificateTemplate;
import com.kbv.education.entity.enums.CertificateType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, UUID> {

    List<CertificateTemplate> findByDeletedFalseOrderByCertificateTypeAscNameAsc();

    Optional<CertificateTemplate> findByIdAndDeletedFalse(UUID id);

    Optional<CertificateTemplate> findByCertificateTypeAndActiveTrueAndDeletedFalse(CertificateType certificateType);

    List<CertificateTemplate> findByCertificateTypeAndActiveTrueAndDeletedFalseAndIdNot(
            CertificateType certificateType, UUID excludeId);
}
