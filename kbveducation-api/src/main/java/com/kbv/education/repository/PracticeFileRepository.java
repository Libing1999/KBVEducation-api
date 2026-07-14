package com.kbv.education.repository;

import com.kbv.education.entity.PracticeFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeFileRepository extends JpaRepository<PracticeFile, UUID> {

    Optional<PracticeFile> findByIdAndDeletedFalse(UUID id);

    List<PracticeFile> findByPracticeSession_IdAndDeletedFalse(UUID practiceSessionId);
}
