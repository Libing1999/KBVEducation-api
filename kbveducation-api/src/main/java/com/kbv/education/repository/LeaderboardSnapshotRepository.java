package com.kbv.education.repository;

import com.kbv.education.entity.LeaderboardSnapshot;
import com.kbv.education.entity.enums.LeaderboardSortField;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaderboardSnapshotRepository extends JpaRepository<LeaderboardSnapshot, UUID> {

    List<LeaderboardSnapshot> findByCohort_IdAndSortByAndDeletedFalseOrderByRankAsc(
            UUID cohortId, LeaderboardSortField sortBy);

    Page<LeaderboardSnapshot> findByCohort_IdAndSortByAndDeletedFalseOrderByRankAsc(
            UUID cohortId, LeaderboardSortField sortBy, Pageable pageable);

    Optional<LeaderboardSnapshot> findByCohort_IdAndStudent_IdAndSortByAndDeletedFalse(
            UUID cohortId, UUID studentId, LeaderboardSortField sortBy);

    @Modifying
    @Query("delete from LeaderboardSnapshot l where l.cohort.id = :cohortId and l.sortBy = :sortBy")
    void deleteByCohortAndSortBy(@Param("cohortId") UUID cohortId, @Param("sortBy") LeaderboardSortField sortBy);
}
