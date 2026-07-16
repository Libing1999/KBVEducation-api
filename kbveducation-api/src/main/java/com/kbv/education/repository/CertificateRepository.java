package com.kbv.education.repository;

import com.kbv.education.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    Optional<Certificate> findByIdAndDeletedFalse(UUID id);

    List<Certificate> findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(UUID studentId);

    List<Certificate> findByDeletedFalseOrderByCreatedAtDesc();
}
