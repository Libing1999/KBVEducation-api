package com.kbv.education.repository;

import com.kbv.education.entity.Certificate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    Optional<Certificate> findByIdAndDeletedFalse(UUID id);

    List<Certificate> findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(UUID studentId);

    List<Certificate> findByDeletedFalseOrderByCreatedAtDesc();

    @Query("select c from Certificate c where c.deleted = false "
            + "and lower(c.certificateNumber) like lower(concat('%', :q, '%'))")
    List<Certificate> search(@Param("q") String query, Pageable pageable);
}
