package com.kbv.education.repository;

import com.kbv.education.entity.AiBatch;
import com.kbv.education.entity.enums.AiBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiBatchRepository extends JpaRepository<AiBatch, UUID> {

    List<AiBatch> findByStatus(AiBatchStatus status);

    long countByStatus(AiBatchStatus status);
}
