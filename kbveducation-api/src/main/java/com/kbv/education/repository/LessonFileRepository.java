package com.kbv.education.repository;

import com.kbv.education.entity.LessonFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonFileRepository extends JpaRepository<LessonFile, UUID> {

    List<LessonFile> findByLesson_IdAndDeletedFalseOrderByUploadedDateAsc(UUID lessonId);

    Optional<LessonFile> findByIdAndDeletedFalse(UUID id);

    long countByLesson_IdAndDeletedFalse(UUID lessonId);

    boolean existsByLesson_IdAndFileNameAndDeletedFalse(UUID lessonId, String fileName);
}
