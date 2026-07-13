package com.kbv.education.repository;

import com.kbv.education.entity.ParentStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ParentStudentRepository extends JpaRepository<ParentStudent, UUID> {

    /** Phase 1: a parent is linked to a single active student. */
    Optional<ParentStudent> findByParent_IdAndDeletedFalse(UUID parentId);

    boolean existsByParent_IdAndStudent_IdAndDeletedFalse(UUID parentId, UUID studentId);
}
