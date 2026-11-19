package com.kbv.education.repository;

import com.kbv.education.entity.AiReferencePackFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiReferencePackFileRepository extends JpaRepository<AiReferencePackFile, UUID> {

    List<AiReferencePackFile> findByPackVersion_IdOrderByFileNameAsc(UUID versionId);

    long countByPackVersion_Id(UUID versionId);
}
