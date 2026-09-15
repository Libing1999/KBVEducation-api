package com.kbv.education.repository;

import com.kbv.education.entity.ParentStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParentStudentRepository extends JpaRepository<ParentStudent, UUID> {

    /** All of a parent's active links, oldest-linked first (a parent may have multiple children). */
    List<ParentStudent> findAllByParent_IdAndDeletedFalseOrderByCreatedAtAsc(UUID parentId);

    Optional<ParentStudent> findByParent_IdAndStudent_IdAndDeletedFalse(UUID parentId, UUID studentId);

    boolean existsByParent_IdAndStudent_IdAndDeletedFalse(UUID parentId, UUID studentId);
}
