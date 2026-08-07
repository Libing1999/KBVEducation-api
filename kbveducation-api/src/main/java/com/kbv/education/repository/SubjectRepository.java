package com.kbv.education.repository;

import com.kbv.education.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    Optional<Subject> findByIdAndDeletedFalse(UUID id);

    List<Subject> findByDeletedFalseOrderByDisplayOrderAsc();

    List<Subject> findByEnabledTrueAndDeletedFalseOrderByDisplayOrderAsc();

    Optional<Subject> findFirstByDeletedFalseOrderByDisplayOrderDesc();

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, UUID id);
}
