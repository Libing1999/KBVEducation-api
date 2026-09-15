package com.kbv.education.service.impl;

import com.kbv.education.audit.Audited;
import com.kbv.education.entity.StudyDay;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.ScoreAuditEntityType;
import com.kbv.education.entity.enums.ScoreTriggerReason;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.StudyDayRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.LeaderboardService;
import com.kbv.education.service.ScoreAuditLogService;
import com.kbv.education.service.ScoreEngineService;
import com.kbv.education.service.StudyDayAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyDayAdminServiceImpl implements StudyDayAdminService {

    private final StudyDayRepository studyDayRepository;
    private final UserRepository userRepository;
    private final ScoreAuditLogService scoreAuditLogService;
    private final ScoreEngineService scoreEngineService;
    private final LeaderboardService leaderboardService;

    @Override
    @Transactional
    @Audited(action = "STUDY_DAY_VOIDED", entityType = "STUDY_DAY")
    public void voidDay(UUID studentId, LocalDate date, String reason, UUID adminId) {
        User student = userRepository.findByIdAndDeletedFalse(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
        User admin = userRepository.findByIdAndDeletedFalse(adminId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", adminId));

        StudyDay day = studyDayRepository.findByStudent_IdAndStudyDateAndDeletedFalse(studentId, date)
                .orElseGet(() -> {
                    StudyDay d = new StudyDay();
                    d.setStudent(student);
                    d.setStudyDate(date);
                    return d;
                });
        if (day.isVoided()) {
            throw new BusinessRuleException("This day is already voided");
        }

        day.setVoided(true);
        day.setVoidedReason(reason);
        day.setVoidedBy(admin);
        day.setVoidedAt(Instant.now());
        studyDayRepository.save(day);

        ScoreAuditEntityType entityType = day.isHasPractice()
                ? ScoreAuditEntityType.PRACTICE
                : ScoreAuditEntityType.REFLECTION;
        scoreAuditLogService.record(entityType, day.getId(), studentId, "STUDY_DAY_VOIDED", null, "voided", reason);

        recalculate(studentId, day);
        log.info("Voided day {} for student {} by admin {}", date, studentId, adminId);
    }

    @Override
    @Transactional
    @Audited(action = "STUDY_DAY_UNVOIDED", entityType = "STUDY_DAY")
    public void unvoidDay(UUID studentId, LocalDate date, UUID adminId) {
        StudyDay day = studyDayRepository.findByStudent_IdAndStudyDateAndDeletedFalse(studentId, date)
                .orElseThrow(() -> new BusinessRuleException("This day was never voided"));
        if (!day.isVoided()) {
            throw new BusinessRuleException("This day is not voided");
        }

        day.setVoided(false);
        String reason = day.getVoidedReason();
        day.setVoidedReason(null);
        day.setVoidedBy(null);
        day.setVoidedAt(null);
        studyDayRepository.save(day);

        ScoreAuditEntityType entityType = day.isHasPractice()
                ? ScoreAuditEntityType.PRACTICE
                : ScoreAuditEntityType.REFLECTION;
        scoreAuditLogService.record(entityType, day.getId(), studentId, "STUDY_DAY_UNVOIDED", "voided", null, reason);

        recalculate(studentId, day);
        log.info("Unvoided day {} for student {} by admin {}", date, studentId, adminId);
    }

    private void recalculate(UUID studentId, StudyDay day) {
        ScoreTriggerReason scoreTrigger = day.isHasPractice()
                ? ScoreTriggerReason.PRACTICE_CHANGE
                : ScoreTriggerReason.REFLECTION_CHANGE;
        scoreEngineService.recalculate(studentId, scoreTrigger);
        leaderboardService.regenerateForStudent(studentId);
    }
}
