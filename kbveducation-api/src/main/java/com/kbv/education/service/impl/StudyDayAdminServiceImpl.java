package com.kbv.education.service.impl;

import com.kbv.education.entity.StudyDay;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.ScoreAuditEntityType;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.StudyDayRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.ScoreAuditLogService;
import com.kbv.education.service.StudyDayAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyDayAdminServiceImpl implements StudyDayAdminService {

    private final StudyDayRepository studyDayRepository;
    private final UserRepository userRepository;
    private final ScoreAuditLogService scoreAuditLogService;

    @Override
    @Transactional
    public void voidDay(UUID studyDayId, String reason, UUID adminId) {
        StudyDay day = studyDayRepository.findByIdAndDeletedFalse(studyDayId)
                .orElseThrow(() -> ResourceNotFoundException.of("Study day", studyDayId));
        if (day.isVoided()) {
            throw new BusinessRuleException("This study day is already voided");
        }

        User admin = userRepository.findByIdAndDeletedFalse(adminId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", adminId));

        day.setVoided(true);
        day.setVoidedReason(reason);
        day.setVoidedBy(admin);
        day.setVoidedAt(Instant.now());
        studyDayRepository.save(day);

        ScoreAuditEntityType entityType = day.isHasPractice()
                ? ScoreAuditEntityType.PRACTICE
                : ScoreAuditEntityType.REFLECTION;
        scoreAuditLogService.record(entityType, day.getId(), day.getStudent().getId(),
                "STUDY_DAY_VOIDED", null, "voided", reason);

        log.info("Voided study day {} by admin {}", studyDayId, adminId);
    }
}
