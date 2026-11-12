package com.kbv.education.repository;

import com.kbv.education.entity.PracticeSession;
import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.entity.enums.StudyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, UUID>,
        JpaSpecificationExecutor<PracticeSession> {

    Optional<PracticeSession> findByIdAndDeletedFalse(UUID id);

    List<PracticeSession> findByStudent_IdAndDeletedFalseOrderByStudyDateDesc(UUID studentId);

    long countByStudent_IdAndDeletedFalse(UUID studentId);

    long countByStudent_IdAndStudyDateBetweenAndDeletedFalse(UUID studentId, LocalDate from, LocalDate to);

    long countByStatusAndDeletedFalse(PracticeStatus status);

    // --- Phase 4 scoring: "Full Papers" = approved past-paper sessions ---
    long countByStudent_IdAndStudyTypeAndStatusAndDeletedFalse(UUID studentId, StudyType studyType, PracticeStatus status);

    long countByStudent_IdAndStudyTypeInAndStatusAndDeletedFalse(UUID studentId, Collection<StudyType> studyTypes,
                                                                  PracticeStatus status);

    // --- Subject Management: block deleting a subject still referenced by a session ---
    boolean existsBySubjectIgnoreCaseAndDeletedFalse(String subject);

    // --- admin statistics ---
    long countByStudyDateAndDeletedFalse(LocalDate date);

    long countByStudyDateBetweenAndDeletedFalse(LocalDate from, LocalDate to);

    // --- distinct practice days (a student can log multiple sessions per day) ---
    @Query("select count(distinct p.studyDate) from PracticeSession p where p.student.id = :sid and p.deleted = false")
    long countDistinctStudyDays(@Param("sid") UUID studentId);

    @Query("select count(distinct p.studyDate) from PracticeSession p "
            + "where p.student.id = :sid and p.deleted = false and p.studyDate between :from and :to")
    long countDistinctStudyDaysBetween(@Param("sid") UUID studentId,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select distinct p.studyDate from PracticeSession p where p.student.id = :sid and p.deleted = false")
    List<LocalDate> practiceDates(@Param("sid") UUID studentId);

    @Query("select p from PracticeSession p where p.deleted = false "
            + "and lower(p.subject) like lower(concat('%', :q, '%'))")
    List<PracticeSession> search(@Param("q") String query, org.springframework.data.domain.Pageable pageable);

    // --- AI engine ---

    /** Sessions submitted since {@code from} that the AI has never been given. */
    @Query("select p.id from PracticeSession p where p.deleted = false and p.createdAt >= :from "
            + "and not exists (select j.id from AiJob j where j.targetId = p.id) order by p.createdAt")
    List<UUID> findIdsNotYetSentToAi(@Param("from") Instant from);

    /** (student id, turn count, character count) of every session of one study type that has turns. */
    @Query("select p.student.id, p.turnCount, p.transcriptChars from PracticeSession p "
            + "where p.deleted = false and p.studyType = :type and p.turnCount is not null and p.turnCount > 0")
    List<Object[]> lengthsForStudyType(@Param("type") StudyType studyType);

    List<PracticeSession> findByAutoApprovedTrueAndDeletedFalseOrderByAutoApprovedAtDesc();

    List<PracticeSession> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDeletedFalse(Instant from, Instant to);

    /** (student id, latest submission time) for every student who has logged practice. */
    @Query("select p.student.id, max(p.createdAt) from PracticeSession p where p.deleted = false group by p.student.id")
    List<Object[]> lastSubmissionPerStudent();

    // --- AI re-analysis and pillar analytics (Phase 2) ---

    @Query("select p.id from PracticeSession p where p.deleted = false and p.studyDate between :from and :to")
    List<UUID> findIdsByStudyDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<PracticeSession> findByStudent_IdInAndStudyDateBetweenAndDeletedFalse(Collection<UUID> studentIds,
                                                                               LocalDate from, LocalDate to);
}
