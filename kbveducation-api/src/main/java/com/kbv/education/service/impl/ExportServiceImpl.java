package com.kbv.education.service.impl;

import com.kbv.education.dto.dashboard.ProgressMetrics;
import com.kbv.education.dto.dashboard.StudentProgressResponse;
import com.kbv.education.dto.export.ExportFormat;
import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.ExportHistory;
import com.kbv.education.entity.LeaderboardSnapshot;
import com.kbv.education.entity.ScoreConfig;
import com.kbv.education.entity.StudentCohort;
import com.kbv.education.entity.StudentScore;
import com.kbv.education.entity.enums.LeaderboardSortField;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.repository.ExportHistoryRepository;
import com.kbv.education.repository.LeaderboardSnapshotRepository;
import com.kbv.education.repository.ScoreConfigRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.StudentScoreRepository;
import com.kbv.education.repository.TierHistoryRepository;
import com.kbv.education.service.ExportService;
import com.kbv.education.service.ProgressService;
import com.kbv.education.utils.TabularExportWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final CohortRepository cohortRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final StudentScoreRepository studentScoreRepository;
    private final LeaderboardSnapshotRepository leaderboardSnapshotRepository;
    private final TierHistoryRepository tierHistoryRepository;
    private final ScoreConfigRepository scoreConfigRepository;
    private final ProgressService progressService;
    private final ExportHistoryRepository exportHistoryRepository;

    @Override
    @Transactional
    public FileDownloadResult exportLeaderboard(UUID cohortId, LeaderboardSortField sortBy, ExportFormat format) {
        Cohort cohort = requireCohort(cohortId);
        LeaderboardSortField effectiveSort = sortBy != null ? sortBy : activeConfig().getLeaderboardSortBy();
        List<LeaderboardSnapshot> snapshots = leaderboardSnapshotRepository
                .findByCohort_IdAndSortByAndDeletedFalseOrderByRankAsc(cohortId, effectiveSort);

        List<String> headers = List.of("Rank", "Student", "Composite Score", "Tier",
                "Practice %", "Reflection %", "Homework %", "Quiz %");
        List<List<Object>> rows = new ArrayList<>();
        for (LeaderboardSnapshot s : snapshots) {
            rows.add(List.of(
                    s.getRank(),
                    s.getStudent().getFullName(),
                    s.getCompositeScore(),
                    s.getCurrentTier() != null ? s.getCurrentTier() : "",
                    s.getPracticePercentage(),
                    s.getReflectionPercentage(),
                    s.getHomeworkPercentage(),
                    s.getQuizPercentage()
            ));
        }
        String fileName = fileName("leaderboard_" + slug(cohort.getName()) + "_" + effectiveSort.name().toLowerCase(), format);
        return buildDownload(fileName, headers, rows, format);
    }

    @Override
    @Transactional
    public FileDownloadResult exportScores(UUID cohortId, ExportFormat format) {
        Cohort cohort = requireCohort(cohortId);
        List<StudentScore> scores = studentScoreRepository.findByCohort_IdAndCurrentTrueAndDeletedFalse(cohortId);

        List<String> headers = List.of("Student", "Composite Score", "Practice %", "Reflection %",
                "Homework %", "Quiz %", "Practice Weight", "Reflection Weight", "Homework Weight",
                "Quiz Weight", "Last Calculated");
        List<List<Object>> rows = new ArrayList<>();
        for (StudentScore s : scores) {
            rows.add(List.of(
                    s.getStudent().getFullName(),
                    s.getCompositeScore(),
                    s.getPracticePercentage(),
                    s.getReflectionPercentage(),
                    s.getHomeworkPercentage(),
                    s.getQuizPercentage(),
                    s.getPracticeWeight(),
                    s.getReflectionWeight(),
                    s.getHomeworkWeight(),
                    s.getQuizWeight(),
                    TIMESTAMP_FORMAT.format(s.getCreatedAt())
            ));
        }
        String fileName = fileName("scores_" + slug(cohort.getName()), format);
        return buildDownload(fileName, headers, rows, format);
    }

    @Override
    @Transactional
    public FileDownloadResult exportTiers(UUID cohortId, ExportFormat format) {
        Cohort cohort = requireCohort(cohortId);
        List<StudentCohort> members = studentCohortRepository.findByCohort_IdAndActiveTrueAndDeletedFalse(cohortId);

        List<String> headers = List.of("Student", "Calculated Tier", "Confirmed Tier", "Overridden",
                "Override Reason", "Composite Score", "Practice %", "Full Papers", "Decided By", "Decided At");
        List<List<Object>> rows = new ArrayList<>();
        for (StudentCohort member : members) {
            UUID studentId = member.getStudent().getId();
            tierHistoryRepository.findFirstByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(studentId)
                    .ifPresent(t -> rows.add(List.of(
                            member.getStudent().getFullName(),
                            t.getCalculatedTier(),
                            t.getConfirmedTier() != null ? t.getConfirmedTier() : "",
                            t.isOverride() ? "Yes" : "No",
                            t.getOverrideReason() != null ? t.getOverrideReason() : "",
                            t.getCompositeScore(),
                            t.getPracticePercentage(),
                            t.getFullPapersCount(),
                            t.getDecidedBy() != null ? t.getDecidedBy().getFullName() : "System",
                            TIMESTAMP_FORMAT.format(t.getCreatedAt())
                    )));
        }
        String fileName = fileName("tiers_" + slug(cohort.getName()), format);
        return buildDownload(fileName, headers, rows, format);
    }

    @Override
    @Transactional
    public FileDownloadResult exportStudentProgress(UUID studentId, ExportFormat format) {
        StudentProgressResponse progress = progressService.getProgressForStudent(studentId);

        List<String> headers = List.of("Period", "Reflection Days", "Practice Days", "Homework Submitted",
                "Quizzes Completed", "Lessons Completed");
        List<List<Object>> rows = List.of(
                periodRow("Current Month", progress.currentMonth()),
                periodRow("Course Total", progress.courseTotal())
        );
        String fileName = fileName("progress_" + slug(progress.studentName()), format);
        return buildDownload(fileName, headers, rows, format);
    }

    private List<Object> periodRow(String label, ProgressMetrics metrics) {
        return List.of(label, metrics.reflectionDays(), metrics.practiceDays(),
                metrics.homeworkSubmitted(), metrics.quizzesCompleted(), metrics.lessonsCompleted());
    }

    private FileDownloadResult buildDownload(String fileName, List<String> headers, List<List<Object>> rows,
                                              ExportFormat format) {
        byte[] bytes = TabularExportWriter.write(format, headers, rows);
        recordHistory(fileName, format, rows.size());
        return new FileDownloadResult(fileName, TabularExportWriter.contentType(format), bytes.length,
                new ByteArrayResource(bytes));
    }

    /** Additive (Phase 5 Step 3): every export run, old dataset or new, gets a history row. */
    private void recordHistory(String fileName, ExportFormat format, int rowCount) {
        String dataset = fileName.contains("leaderboard") ? "LEADERBOARD"
                : fileName.contains("scores") ? "COMPOSITE_SCORES"
                : fileName.contains("tiers") ? "TIER_HISTORY"
                : "PROGRESS";
        ExportHistory history = new ExportHistory();
        history.setDataset(dataset);
        history.setFormat(format.name());
        history.setRowCount(rowCount);
        exportHistoryRepository.save(history);
    }

    private String fileName(String base, ExportFormat format) {
        return base + "." + TabularExportWriter.extension(format);
    }

    private String slug(String value) {
        return value == null ? "export" : value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    private Cohort requireCohort(UUID cohortId) {
        return cohortRepository.findByIdAndDeletedFalse(cohortId)
                .orElseThrow(() -> ResourceNotFoundException.of("Cohort", cohortId));
    }

    private ScoreConfig activeConfig() {
        return scoreConfigRepository.findByActiveTrueAndDeletedFalse()
                .orElseThrow(() -> new ResourceNotFoundException("No active score configuration found"));
    }
}
