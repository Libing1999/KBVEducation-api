package com.kbv.education.repository;

import com.kbv.education.entity.AiFieldDefinition;
import com.kbv.education.entity.enums.AiJobType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiFieldDefinitionRepository extends JpaRepository<AiFieldDefinition, UUID> {

    List<AiFieldDefinition> findByJobTypeAndActiveTrueAndDeletedFalseOrderByDisplayOrderAscCreatedAtAsc(AiJobType jobType);

    List<AiFieldDefinition> findByDeletedFalseOrderByJobTypeAscDisplayOrderAscCreatedAtAsc();

    boolean existsByJobTypeAndFieldKey(AiJobType jobType, String fieldKey);
}
