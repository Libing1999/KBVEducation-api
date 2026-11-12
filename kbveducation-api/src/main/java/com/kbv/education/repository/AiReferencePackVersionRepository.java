package com.kbv.education.repository;

import com.kbv.education.entity.AiReferencePackVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiReferencePackVersionRepository extends JpaRepository<AiReferencePackVersion, UUID> {

    Optional<AiReferencePackVersion> findByActiveTrue();

    List<AiReferencePackVersion> findAllByOrderByVersionNoDesc();

    @Query("select coalesce(max(v.versionNo), 0) from AiReferencePackVersion v")
    int maxVersionNo();
}
