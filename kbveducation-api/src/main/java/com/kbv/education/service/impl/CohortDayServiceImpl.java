package com.kbv.education.service.impl;

import com.kbv.education.audit.Audited;
import com.kbv.education.dto.cohortday.CohortDayResponse;
import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.CohortDay;
import com.kbv.education.entity.enums.CohortDayType;
import com.kbv.education.entity.enums.ScoreTriggerReason;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.CohortDayRepository;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.service.CohortDayService;
import com.kbv.education.service.ScoreEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CohortDayServiceImpl implements CohortDayService {

    private final CohortDayRepository cohortDayRepository;
    private final CohortRepository cohortRepository;
    private final ScoreEngineService scoreEngineService;

    @Override
    @Transactional(readOnly = true)
    public List<CohortDayResponse> list(UUID cohortId, LocalDate from, LocalDate to) {
        Map<LocalDate, CohortDay> configured = cohortDayRepository
                .findByCohort_IdAndDateBetweenAndDeletedFalse(cohortId, from, to).stream()
                .collect(Collectors.toMap(CohortDay::getDate, d -> d));

        return from.datesUntil(to.plusDays(1))
                .map(date -> {
                    CohortDay day = configured.get(date);
                    return day != null
                            ? new CohortDayResponse(date, day.getDayType(), true)
                            : new CohortDayResponse(date, CohortDayType.LESSON_DAY, false);
                })
                .toList();
    }

    @Override
    @Transactional
    @Audited(action = "COHORT_DAY_SET", entityType = "COHORT_DAY")
    public CohortDayResponse upsert(UUID cohortId, LocalDate date, CohortDayType dayType, UUID adminId) {
        Cohort cohort = cohortRepository.findByIdAndDeletedFalse(cohortId)
                .orElseThrow(() -> ResourceNotFoundException.of("Cohort", cohortId));

        CohortDay day = cohortDayRepository.findByCohort_IdAndDate(cohortId, date)
                .orElseGet(() -> {
                    CohortDay d = new CohortDay();
                    d.setCohort(cohort);
                    d.setDate(date);
                    return d;
                });
        day.setDeleted(false);
        day.setDayType(dayType);
        cohortDayRepository.save(day);

        recalculateCohort(cohortId);
        log.info("Set cohort {} date {} to {} by admin {}", cohortId, date, dayType, adminId);
        return new CohortDayResponse(date, dayType, true);
    }

    @Override
    @Transactional
    @Audited(action = "COHORT_DAY_RESET", entityType = "COHORT_DAY")
    public void reset(UUID cohortId, LocalDate date, UUID adminId) {
        cohortDayRepository.findByCohort_IdAndDate(cohortId, date).ifPresent(day -> {
            day.setDeleted(true);
            cohortDayRepository.save(day);
            recalculateCohort(cohortId);
            log.info("Reset cohort {} date {} to default by admin {}", cohortId, date, adminId);
        });
    }

    private void recalculateCohort(UUID cohortId) {
        scoreEngineService.recalculateForCohort(cohortId, ScoreTriggerReason.COHORT_DAY_CHANGE);
    }
}
