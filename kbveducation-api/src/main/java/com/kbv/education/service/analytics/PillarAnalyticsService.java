package com.kbv.education.service.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.dto.analytics.PillarAnalyticsResponse;
import com.kbv.education.dto.analytics.PillarAnalyticsResponse.Chart;
import com.kbv.education.dto.analytics.PillarAnalyticsResponse.Point;
import com.kbv.education.dto.analytics.PillarAnalyticsResponse.Row;
import com.kbv.education.dto.analytics.PillarAnalyticsResponse.Series;
import com.kbv.education.dto.analytics.PillarAnalyticsResponse.Stat;
import com.kbv.education.dto.analytics.PillarAnalyticsResponse.StudentRef;
import com.kbv.education.dto.analytics.PillarAnalyticsResponse.Table;
import com.kbv.education.entity.AiFieldDefinition;
import com.kbv.education.entity.AiResult;
import com.kbv.education.entity.Homework;
import com.kbv.education.entity.HomeworkSubmission;
import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.PracticeSession;
import com.kbv.education.entity.QuizAnswer;
import com.kbv.education.entity.QuizAttempt;
import com.kbv.education.entity.QuizQuestion;
import com.kbv.education.entity.ReflectionAnswer;
import com.kbv.education.entity.ReflectionEntry;
import com.kbv.education.entity.StudentCohort;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.AiFieldType;
import com.kbv.education.entity.enums.AiJobType;
import com.kbv.education.entity.enums.AttemptStatus;
import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.entity.enums.ReflectionType;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.StudyType;
import com.kbv.education.entity.enums.TranscriptStatus;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.repository.AiFieldDefinitionRepository;
import com.kbv.education.repository.AiResultRepository;
import com.kbv.education.repository.HomeworkRepository;
import com.kbv.education.repository.HomeworkSubmissionFileRepository;
import com.kbv.education.repository.HomeworkSubmissionRepository;
import com.kbv.education.repository.PracticeSessionRepository;
import com.kbv.education.repository.QuizAnswerRepository;
import com.kbv.education.repository.QuizAttemptRepository;
import com.kbv.education.repository.ReflectionAnswerRepository;
import com.kbv.education.repository.ReflectionEntryRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.ai.AiSettingsService;
import com.kbv.education.service.ai.TranscriptTurns;
import com.kbv.education.service.analytics.SeriesBuilder.Agg;
import com.kbv.education.service.analytics.SeriesBuilder.Obs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The new Analytics screen (memo "AI plan" 17.10.2026, section 2): one pillar at a time
 * (Practice, Reflection, Homework, Quiz) for one student, several, a cohort or the whole
 * class, over any dates. The AI does the reading and judging; this class does the counting.
 * Everything is built from saved data: a new graph never needs old sessions to be run again.
 */
@Service
@RequiredArgsConstructor
public class PillarAnalyticsService {

    public enum Pillar { PRACTICE, REFLECTION, HOMEWORK, QUIZ }

    /** "Who" and "Dates". studentIds wins over cohortId; neither means the whole class. */
    public record Query(Set<UUID> studentIds, UUID cohortId, LocalDate from, LocalDate to, PracticeStatus status) {
    }

    private static final Pattern SCORE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)");
    private static final int MAX_DAYS = 3 * 366;
    private static final int TOP = 20;

    private final UserRepository userRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final PracticeSessionRepository practiceRepository;
    private final ReflectionEntryRepository reflectionRepository;
    private final ReflectionAnswerRepository reflectionAnswerRepository;
    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final HomeworkSubmissionFileRepository submissionFileRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final AiResultRepository resultRepository;
    private final AiFieldDefinitionRepository fieldRepository;
    private final AiSettingsService settingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PillarAnalyticsResponse build(Pillar pillar, Query q) {
        if (q.from() == null || q.to() == null || q.to().isBefore(q.from())) {
            throw new BadRequestException("Choose a start date on or before the end date");
        }
        if (ChronoUnit.DAYS.between(q.from(), q.to()) > MAX_DAYS) {
            throw new BadRequestException("Choose a range of three years or less");
        }
        List<User> students = resolveStudents(q);
        boolean single = q.studentIds() != null && q.studentIds().size() == 1 && students.size() == 1;
        Ctx ctx = new Ctx(q, students, single,
                students.stream().collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a, LinkedHashMap::new)),
                ZoneId.of(settingsService.getActive().getRunTimezone()));

        Built built = switch (pillar) {
            case PRACTICE -> practice(ctx);
            case REFLECTION -> reflection(ctx);
            case HOMEWORK -> homework(ctx);
            case QUIZ -> quiz(ctx);
        };
        List<StudentRef> refs = students.stream().map(u -> new StudentRef(u.getId(), u.getFullName())).toList();
        return new PillarAnalyticsResponse(pillar.name(), q.from(), q.to(), single, refs,
                built.stats(), built.charts(), built.tables());
    }

    private record Ctx(Query q, List<User> students, boolean single, Map<UUID, String> names, ZoneId zone) {
        Set<UUID> ids() {
            return names.keySet();
        }
    }

    private record Built(List<Stat> stats, List<Chart> charts, List<Table> tables) {
    }

    private List<User> resolveStudents(Query q) {
        List<User> list;
        if (q.studentIds() != null && !q.studentIds().isEmpty()) {
            list = userRepository.findAllById(q.studentIds()).stream()
                    .filter(u -> !u.isDeleted() && u.getRole().getName() == RoleType.STUDENT)
                    .toList();
        } else if (q.cohortId() != null) {
            list = studentCohortRepository.findByCohort_IdAndActiveTrueAndDeletedFalse(q.cohortId()).stream()
                    .map(StudentCohort::getStudent)
                    .filter(u -> !u.isDeleted())
                    .toList();
        } else {
            list = userRepository.findByRole_NameAndStatusAndDeletedFalse(RoleType.STUDENT, UserStatus.ACTIVE);
        }
        return list.stream().sorted(Comparator.comparing(User::getFullName)).toList();
    }

    // =================================================================== Practice (2.2)

    private Built practice(Ctx c) {
        List<PracticeSession> sessions = c.ids().isEmpty() ? List.of()
                : practiceRepository.findByStudent_IdInAndStudyDateBetweenAndDeletedFalse(c.ids(), c.q().from(), c.q().to())
                        .stream()
                        .filter(s -> c.q().status() == null || s.getStatus() == c.q().status())
                        .toList();
        Map<UUID, JsonNode> ai = results(sessions.stream().map(PracticeSession::getId).toList());

        List<Obs> minutes = new ArrayList<>();
        List<Obs> chars = new ArrayList<>();
        List<Obs> messages = new ArrayList<>();
        List<Obs> perDay = new ArrayList<>();
        List<Obs> engagement = new ArrayList<>();
        Map<String, List<Obs>> scoresBySubject = new LinkedHashMap<>();
        Set<String> approvedStudentDays = new LinkedHashSet<>();
        List<Obs> studyDays = new ArrayList<>();
        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, Long> bySubject = new LinkedHashMap<>();
        Map<DayOfWeek, Long> byWeekday = new LinkedHashMap<>();
        Map<String, Map<String, Long>> topicDepth = new LinkedHashMap<>();
        Map<String, Long> weak = new LinkedHashMap<>();

        for (PracticeSession s : sessions) {
            UUID sid = s.getStudent().getId();
            LocalDate d = s.getStudyDate();
            int turns = s.getTurnCount() != null ? s.getTurnCount()
                    : TranscriptTurns.split(s.getTranscript()).turns().size();
            int length = s.getTranscriptChars() != null ? s.getTranscriptChars()
                    : s.getTranscript() == null ? 0 : s.getTranscript().length();

            minutes.add(new Obs(d, sid, s.getDurationMinutes(), s.getId(), null));
            if (length > 0) {
                chars.add(new Obs(d, sid, length, s.getId(), null));
                messages.add(new Obs(d, sid, turns, s.getId(), null));
            }
            perDay.add(new Obs(d, sid, 1, s.getId(), null));
            if (s.getStatus() == PracticeStatus.APPROVED && approvedStudentDays.add(sid + "|" + d)) {
                studyDays.add(new Obs(d, sid, 1, s.getId(), null));
            }
            byType.merge(studyTypeLabel(s.getStudyType()), 1L, Long::sum);
            bySubject.merge(s.getSubject(), 1L, Long::sum);
            byWeekday.merge(d.getDayOfWeek(), 1L, Long::sum);

            JsonNode r = ai.get(s.getId());
            if (r == null) {
                continue;
            }
            if (r.path("engagement_rating").isInt()) {
                engagement.add(new Obs(d, sid, r.path("engagement_rating").asInt(), s.getId(), null));
            }
            if (s.getStudyType() == StudyType.PAST_PAPER_TEST_DAY) {
                String raw = r.path("past_paper_score").asText("");
                Matcher m = SCORE.matcher(raw);
                if (m.find() && Double.parseDouble(m.group(2)) > 0) {
                    double pct = 100.0 * Double.parseDouble(m.group(1)) / Double.parseDouble(m.group(2));
                    scoresBySubject.computeIfAbsent(s.getSubject(), k -> new ArrayList<>())
                            .add(new Obs(d, sid, pct, s.getId(), raw.strip()));
                }
            }
            for (JsonNode t : r.path("topics")) {
                topicDepth.computeIfAbsent(t.path("topic").asText(), k -> new LinkedHashMap<>())
                        .merge(t.path("depth").asText(), 1L, Long::sum);
            }
            for (JsonNode w : r.path("weak_topics")) {
                weak.merge(w.asText(), 1L, Long::sum);
            }
        }

        boolean per = c.single();
        List<Chart> charts = new ArrayList<>();
        List<Series> scoreSeries = scoresBySubject.entrySet().stream()
                .map(e -> SeriesBuilder.series(e.getKey(), e.getValue(), Agg.AVG, per, c.names()))
                .toList();
        charts.add(new Chart("past_paper_score", "Past paper score (one line per subject, one point per sitting)",
                "%", "line", "avg", "practice", false, scoreSeries));
        charts.add(line("engagement", "Engagement (1 to 10)", "", Agg.AVG, per, "practice", engagement, c));
        charts.add(line("minutes", "Session length (minutes)", "min", Agg.AVG, per, "practice", minutes, c));
        charts.add(line("characters", "Session length (characters)", "chars", Agg.AVG, per, "practice", chars, c));
        charts.add(line("messages", "Messages per session", "", Agg.AVG, per, "practice", messages, c));
        charts.add(line("sessions_per_day", "Sessions per day", "", Agg.SUM, false, "none", perDay, c));
        charts.add(line("study_days", c.single() ? "Study days (approved session)" : "Students with a study day",
                "", Agg.SUM, false, "none", studyDays, c));
        charts.addAll(customLineCharts(AiJobType.PRACTICE_REVIEW, sessions, PracticeSession::getId,
                s -> s.getStudent().getId(), PracticeSession::getStudyDate, ai, c, "practice"));

        charts.add(bar("by_study_type", "Sessions by study type", byType));
        charts.add(bar("by_subject", "Sessions by subject", bySubject));
        Map<String, Long> weekdays = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            weekdays.put(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH), byWeekday.getOrDefault(day, 0L));
        }
        charts.add(bar("by_weekday", "Sessions by weekday", weekdays));
        charts.add(topicsChart(topicDepth));
        charts.add(bar("weak_topics", "Most common weak topics", top(weak)));

        List<Table> tables = new ArrayList<>(customTextTables(AiJobType.PRACTICE_REVIEW, sessions,
                PracticeSession::getId, s -> s.getStudent().getId(), PracticeSession::getStudyDate, ai, c, "practice"));

        long students = sessions.stream().map(s -> s.getStudent().getId()).distinct().count();
        List<Stat> stats = List.of(
                new Stat("Sessions", String.valueOf(sessions.size())),
                new Stat("Students with a session", students + " of " + c.students().size()),
                new Stat("Study days (approved)", String.valueOf(approvedStudentDays.size())),
                new Stat("Average minutes", avg(minutes)),
                new Stat("Average messages per session", avg(messages)),
                new Stat("Analysed by the AI", ai.size() + " of " + sessions.size()));
        return new Built(stats, charts, tables);
    }

    private Chart topicsChart(Map<String, Map<String, Long>> topicDepth) {
        List<String> topTopics = topicDepth.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Map<String, Long>> e) ->
                        e.getValue().values().stream().mapToLong(Long::longValue).sum()).reversed())
                .limit(TOP)
                .map(Map.Entry::getKey)
                .toList();
        List<Series> series = new ArrayList<>();
        for (String depth : List.of("touched", "practised", "secure")) {
            List<Point> points = topTopics.stream()
                    .map(t -> new Point(t, topicDepth.get(t).getOrDefault(depth, 0L), null, null, null))
                    .toList();
            series.add(new Series(depth, points));
        }
        return new Chart("topics", "Topics studied and how deeply (sessions)", "", "bar", "sum", "none", true, series);
    }

    // =================================================================== Reflection (2.3)

    private Built reflection(Ctx c) {
        List<ReflectionEntry> entries = c.ids().isEmpty() ? List.of()
                : reflectionRepository.findByStudent_IdInAndReflectionDateBetweenAndDeletedFalse(
                        c.ids(), c.q().from(), c.q().to());
        Map<UUID, JsonNode> ai = results(entries.stream().map(ReflectionEntry::getId).toList());
        Map<UUID, Integer> typedWords = new HashMap<>();
        if (!entries.isEmpty()) {
            for (ReflectionAnswer a : reflectionAnswerRepository.findByReflectionEntry_IdInAndDeletedFalse(
                    entries.stream().map(ReflectionEntry::getId).toList())) {
                typedWords.merge(a.getReflectionEntry().getId(), words(a.getAnswerText()), Integer::sum);
            }
        }

        List<Obs> reflected = new ArrayList<>();
        List<Obs> wordsPer = new ArrayList<>();
        Map<String, Long> kinds = new LinkedHashMap<>();
        kinds.put("Typed", 0L);
        kinds.put("Voice", 0L);
        kinds.put("Typed and voice", 0L);
        Map<String, Long> flags = new LinkedHashMap<>();
        flags.put("worrying", 0L);
        flags.put("disengaged", 0L);
        flags.put("overloaded", 0L);
        Map<UUID, Integer> countPerStudent = new HashMap<>();
        List<Row> summaryRows = new ArrayList<>();

        for (ReflectionEntry r : entries.stream().sorted(Comparator.comparing(ReflectionEntry::getReflectionDate)).toList()) {
            UUID sid = r.getStudent().getId();
            LocalDate d = r.getReflectionDate();
            int w = typedWords.getOrDefault(r.getId(), 0)
                    + (r.getTranscriptStatus() == TranscriptStatus.DONE ? words(r.getTranscript()) : 0);
            reflected.add(new Obs(d, sid, 1, r.getId(), null));
            if (w > 0) {
                wordsPer.add(new Obs(d, sid, w, r.getId(), null));
            }
            kinds.merge(kindLabel(r.getReflectionType()), 1L, Long::sum);
            countPerStudent.merge(sid, 1, Integer::sum);

            JsonNode res = ai.get(r.getId());
            List<String> flagNames = new ArrayList<>();
            if (res != null) {
                for (JsonNode f : res.path("flags")) {
                    flags.merge(f.path("type").asText(), 1L, Long::sum);
                    flagNames.add(f.path("type").asText());
                }
            }
            summaryRows.add(new Row(List.of(d.toString(), c.names().getOrDefault(sid, ""), kindLabel(r.getReflectionType()),
                    String.valueOf(w), String.join(", ", flagNames),
                    res == null ? "" : res.path("summary").asText("")), "reflection", r.getId()));
        }

        boolean per = c.single();
        List<Chart> charts = new ArrayList<>();
        charts.add(line("reflected", c.single() ? "Reflected (one point per reflection)" : "Students who reflected",
                "", Agg.SUM, false, "none", reflected, c));
        charts.add(line("words", "Words per reflection (typed + voice transcript)", "words", Agg.AVG, per,
                "reflection", wordsPer, c));
        charts.addAll(customLineCharts(AiJobType.REFLECTION_REVIEW, entries, ReflectionEntry::getId,
                r -> r.getStudent().getId(), ReflectionEntry::getReflectionDate, ai, c, "reflection"));
        charts.add(bar("typed_vs_voice", "Typed or voice", kinds));
        charts.add(bar("flags", "AI flags by type", flags));

        long days = ChronoUnit.DAYS.between(c.q().from(), c.q().to()) + 1;
        List<Row> consistency = c.students().stream()
                .map(u -> {
                    int n = countPerStudent.getOrDefault(u.getId(), 0);
                    return new Row(List.of(u.getFullName(), String.valueOf(n), String.valueOf(days),
                            Math.round(100.0 * n / days) + "%"), "student", u.getId());
                })
                .toList();
        List<Table> tables = new ArrayList<>();
        tables.add(new Table("consistency", "Consistency: how often each student reflected",
                List.of("Student", "Reflections", "Days in range", "Share of days"), consistency));
        tables.add(new Table("summaries", "Reflections and the AI's summaries",
                List.of("Date", "Student", "Type", "Words", "Flags", "AI summary"), summaryRows));
        tables.addAll(customTextTables(AiJobType.REFLECTION_REVIEW, entries, ReflectionEntry::getId,
                r -> r.getStudent().getId(), ReflectionEntry::getReflectionDate, ai, c, "reflection"));

        long flagged = ai.values().stream().filter(n -> n.path("flags").size() > 0).count();
        List<Stat> stats = List.of(
                new Stat("Reflections", String.valueOf(entries.size())),
                new Stat("Students who reflected", countPerStudent.size() + " of " + c.students().size()),
                new Stat("Average words", avg(wordsPer)),
                new Stat("With a voice note", String.valueOf(kinds.get("Voice") + kinds.get("Typed and voice"))),
                new Stat("Flagged by the AI", String.valueOf(flagged)));
        return new Built(stats, charts, tables);
    }

    // =================================================================== Homework (2.3)

    private Built homework(Ctx c) {
        // Every cohort each student is or was in, so homework set before a move still counts.
        Map<UUID, Set<UUID>> cohortsOf = new HashMap<>();
        if (!c.ids().isEmpty()) {
            for (StudentCohort sc : studentCohortRepository.findByStudent_IdInAndDeletedFalse(c.ids())) {
                cohortsOf.computeIfAbsent(sc.getStudent().getId(), k -> new LinkedHashSet<>()).add(sc.getCohort().getId());
            }
        }
        Set<UUID> allCohorts = cohortsOf.values().stream().flatMap(Set::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Instant from = c.q().from().atStartOfDay(c.zone()).toInstant();
        Instant to = c.q().to().plusDays(1).atStartOfDay(c.zone()).toInstant();
        // Submissions handed in during the chosen dates (the per-day graphs and totals).
        List<HomeworkSubmission> subs = c.ids().isEmpty() ? List.of()
                : submissionRepository.findByStudent_IdInAndSubmittedAtBetweenAndDeletedFalse(c.ids(), from, to).stream()
                        .filter(s -> !s.getHomework().isDeleted())
                        .toList();
        // Homework for the per-lesson view: due in the chosen dates, plus homework with no due date
        // that was handed in during them.
        Map<UUID, Homework> hwById = new LinkedHashMap<>();
        if (!allCohorts.isEmpty()) {
            homeworkRepository.findByLesson_Cohort_IdInAndDueDateBetweenAndDeletedFalse(allCohorts, from, to)
                    .forEach(h -> hwById.put(h.getId(), h));
        }
        subs.stream().map(HomeworkSubmission::getHomework).filter(h -> h.getDueDate() == null)
                .forEach(h -> hwById.putIfAbsent(h.getId(), h));
        List<Homework> homework = hwById.values().stream()
                .sorted(Comparator.comparing(Homework::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<HomeworkSubmission> statusSubs = homework.isEmpty() ? List.of()
                : submissionRepository.findByHomework_IdInAndDeletedFalse(hwById.keySet()).stream()
                        .filter(s -> c.ids().contains(s.getStudent().getId())).toList();
        Map<UUID, Long> files = new HashMap<>();
        if (!subs.isEmpty()) {
            for (Object[] row : submissionFileRepository.countFilesBySubmission(subs.stream().map(HomeworkSubmission::getId).toList())) {
                files.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }
        Map<String, HomeworkSubmission> subByKey = new HashMap<>();
        for (HomeworkSubmission s : statusSubs) {
            subByKey.put(s.getHomework().getId() + "|" + s.getStudent().getId(), s);
        }

        List<Obs> perDay = new ArrayList<>();
        List<Obs> wordsPer = new ArrayList<>();
        Map<String, Long> kinds = new LinkedHashMap<>();
        kinds.put("Text only", 0L);
        kinds.put("Files only", 0L);
        kinds.put("Text and files", 0L);
        for (HomeworkSubmission s : subs) {
            LocalDate d = s.getSubmittedAt().atZone(c.zone()).toLocalDate();
            UUID sid = s.getStudent().getId();
            perDay.add(new Obs(d, sid, 1, s.getId(), null));
            int w = words(s.getNote());
            if (w > 0) {
                wordsPer.add(new Obs(d, sid, w, s.getId(), null));
            }
            boolean hasFiles = files.getOrDefault(s.getId(), 0L) > 0;
            kinds.merge(w > 0 && hasFiles ? "Text and files" : hasFiles ? "Files only" : "Text only", 1L, Long::sum);
        }

        // Per lesson: on time, late, not submitted (expected = the chosen students in that lesson's cohort).
        List<String> lessonLabels = new ArrayList<>();
        List<Point> onTime = new ArrayList<>();
        List<Point> late = new ArrayList<>();
        List<Point> missing = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        long expectedTotal = 0;
        long onTimeTotal = 0;
        long submittedTotal = 0;
        for (Homework h : homework) {
            Lesson lesson = h.getLesson();
            String label = "L" + lesson.getLessonNumber() + " " + lesson.getTitle();
            lessonLabels.add(label);
            long ok = 0;
            long lt = 0;
            long miss = 0;
            for (User u : c.students()) {
                if (!cohortsOf.getOrDefault(u.getId(), Set.of()).contains(lesson.getCohort().getId())) {
                    continue;
                }
                HomeworkSubmission s = subByKey.get(h.getId() + "|" + u.getId());
                String status;
                if (s == null) {
                    miss++;
                    status = "Not submitted";
                } else if (h.getDueDate() == null || !s.getSubmittedAt().isAfter(h.getDueDate())) {
                    ok++;
                    status = "On time";
                } else {
                    lt++;
                    status = "Late";
                }
                rows.add(new Row(List.of(label, h.getTitle(), u.getFullName(),
                        h.getDueDate() == null ? "" : h.getDueDate().atZone(c.zone()).toLocalDate().toString(),
                        s == null ? "" : s.getSubmittedAt().atZone(c.zone()).toLocalDate().toString(), status),
                        "student", u.getId()));
            }
            onTime.add(new Point(label, ok, null, null, null));
            late.add(new Point(label, lt, null, null, null));
            missing.add(new Point(label, miss, null, null, null));
            expectedTotal += ok + lt + miss;
            onTimeTotal += ok;
            submittedTotal += ok + lt;
        }

        boolean per = c.single();
        List<Chart> charts = new ArrayList<>();
        charts.add(line("submissions", "Homework submitted per day", "", Agg.SUM, false, "none", perDay, c));
        charts.add(line("words", "Words per submission (text part)", "words", Agg.AVG, per, "none", wordsPer, c));
        charts.add(new Chart("per_lesson", "Per lesson: on time, late, not submitted", "", "bar", "sum", "none", true,
                List.of(new Series("On time", onTime), new Series("Late", late), new Series("Not submitted", missing))));
        charts.add(bar("text_or_files", "Text or files", kinds));

        List<Table> tables = List.of(new Table("status", "Every student and homework",
                List.of("Lesson", "Homework", "Student", "Due", "Submitted", "Status"), rows));
        List<Stat> stats = List.of(
                new Stat("Homework in range", String.valueOf(homework.size())),
                new Stat("Submitted", submittedTotal + " of " + expectedTotal),
                new Stat("On time", expectedTotal == 0 ? "—" : Math.round(100.0 * onTimeTotal / expectedTotal) + "%"),
                new Stat("Not submitted", String.valueOf(expectedTotal - submittedTotal)));
        return new Built(stats, charts, tables);
    }

    // =================================================================== Quiz (2.4)

    private Built quiz(Ctx c) {
        Instant from = c.q().from().atStartOfDay(c.zone()).toInstant();
        Instant to = c.q().to().plusDays(1).atStartOfDay(c.zone()).toInstant();
        List<QuizAttempt> attempts = c.ids().isEmpty() ? List.of()
                : quizAttemptRepository.findByStudent_IdInAndStatusAndSubmittedAtBetweenAndDeletedFalse(
                        c.ids(), AttemptStatus.SUBMITTED, from, to).stream()
                        .filter(a -> a.getScore() != null && a.getMaxScore() != null && a.getMaxScore() > 0)
                        .toList();
        List<QuizAnswer> answers = attempts.isEmpty() ? List.of()
                : quizAnswerRepository.findByAttempt_IdInAndDeletedFalse(attempts.stream().map(QuizAttempt::getId).toList());

        List<Obs> scores = new ArrayList<>();
        Map<String, List<Double>> byLesson = new LinkedHashMap<>();
        for (QuizAttempt a : attempts.stream().sorted(Comparator.comparing(QuizAttempt::getSubmittedAt)).toList()) {
            double pct = 100.0 * a.getScore() / a.getMaxScore();
            Lesson lesson = a.getQuiz().getLesson();
            scores.add(new Obs(a.getSubmittedAt().atZone(c.zone()).toLocalDate(), a.getStudent().getId(), pct,
                    a.getId(), a.getScore() + "/" + a.getMaxScore() + " · " + a.getQuiz().getTitle()));
            byLesson.computeIfAbsent("L" + lesson.getLessonNumber() + " " + lesson.getTitle(), k -> new ArrayList<>())
                    .add(pct);
        }

        // Percentage correct per question (only questions that were marked right or wrong).
        Map<UUID, QuizQuestion> questions = new LinkedHashMap<>();
        Map<UUID, int[]> tally = new LinkedHashMap<>();
        for (QuizAnswer ans : answers) {
            if (ans.getCorrect() == null) {
                continue;
            }
            QuizQuestion qq = ans.getQuestion();
            questions.putIfAbsent(qq.getId(), qq);
            int[] t = tally.computeIfAbsent(qq.getId(), k -> new int[2]);
            t[0]++;
            if (Boolean.TRUE.equals(ans.getCorrect())) {
                t[1]++;
            }
        }
        List<Point> perQuestion = new ArrayList<>();
        List<Row> questionRows = new ArrayList<>();
        questions.values().stream()
                .sorted(Comparator.comparing((QuizQuestion qq) -> qq.getQuiz().getLesson().getLessonNumber())
                        .thenComparing(QuizQuestion::getDisplayOrder))
                .forEach(qq -> {
                    int[] t = tally.get(qq.getId());
                    double pct = 100.0 * t[1] / t[0];
                    String name = "L" + qq.getQuiz().getLesson().getLessonNumber() + " Q" + (qq.getDisplayOrder() + 1);
                    perQuestion.add(new Point(name, SeriesBuilder.round(pct), shorten(qq.getQuestionText(), 80), null, null));
                    questionRows.add(new Row(List.of(name, qq.getQuiz().getTitle(), shorten(qq.getQuestionText(), 140),
                            String.valueOf(t[0]), Math.round(pct) + "%"), "none", null));
                });

        Map<String, Double> lessonAvg = new LinkedHashMap<>();
        byLesson.forEach((k, v) -> lessonAvg.put(k, SeriesBuilder.round(v.stream().mapToDouble(Double::doubleValue).average().orElse(0))));

        boolean per = c.single();
        List<Chart> charts = new ArrayList<>();
        charts.add(line("score", "Quiz score", "%", Agg.AVG, per, "quiz", scores, c));
        charts.add(new Chart("avg_by_lesson", "Average score per lesson", "%", "bar", "avg", "none", false,
                List.of(new Series("Average score",
                        lessonAvg.entrySet().stream().map(e -> new Point(e.getKey(), e.getValue(), null, null, null)).toList()))));
        charts.add(new Chart("correct_by_question", "Percentage correct per question", "%", "bar", "avg", "none", false,
                List.of(new Series("% correct", perQuestion))));

        List<Table> tables = List.of(new Table("questions", "Every question",
                List.of("Question", "Quiz", "Text", "Answers", "% correct"), questionRows));
        List<Stat> stats = List.of(
                new Stat("Quiz attempts", String.valueOf(attempts.size())),
                new Stat("Average score", scores.isEmpty() ? "—" : avg(scores) + "%"),
                new Stat("Students who took a quiz",
                        attempts.stream().map(a -> a.getStudent().getId()).distinct().count() + " of " + c.students().size()));
        return new Built(stats, charts, tables);
    }

    // =================================================================== custom AI fields (2.1)

    /** Numbers, ratings and yes/no fields become graphs over time (yes/no as the share of "yes"). */
    private <T> List<Chart> customLineCharts(AiJobType type, List<T> items, Function<T, UUID> id,
                                             Function<T, UUID> student, Function<T, LocalDate> date,
                                             Map<UUID, JsonNode> ai, Ctx c, String drill) {
        List<Chart> charts = new ArrayList<>();
        for (AiFieldDefinition f : fields(type)) {
            if (!f.getFieldType().isGraphed()) {
                continue;
            }
            List<Obs> obs = new ArrayList<>();
            for (T item : items) {
                JsonNode v = extra(ai.get(id.apply(item)), f);
                if (v == null) {
                    continue;
                }
                Double value = switch (f.getFieldType()) {
                    case NUMBER, RATING -> v.isNumber() ? v.asDouble() : null;
                    case YES_NO -> v.isBoolean() ? (v.asBoolean() ? 100.0 : 0.0) : null;
                    default -> null;
                };
                if (value != null) {
                    obs.add(new Obs(date.apply(item), student.apply(item), value, id.apply(item),
                            f.getFieldType() == AiFieldType.YES_NO ? (v.asBoolean() ? "yes" : "no") : null));
                }
            }
            String unit = f.getFieldType() == AiFieldType.YES_NO ? "% yes" : "";
            charts.add(line("extra_" + f.getFieldKey(), f.getLabel() + (f.isActive() ? "" : " (switched off)"), unit,
                    Agg.AVG, c.single(), drill, obs, c));
        }
        return charts;
    }

    /** Short text and list fields become tables. */
    private <T> List<Table> customTextTables(AiJobType type, List<T> items, Function<T, UUID> id,
                                             Function<T, UUID> student, Function<T, LocalDate> date,
                                             Map<UUID, JsonNode> ai, Ctx c, String refType) {
        List<Table> tables = new ArrayList<>();
        for (AiFieldDefinition f : fields(type)) {
            if (f.getFieldType().isGraphed()) {
                continue;
            }
            List<Row> rows = new ArrayList<>();
            for (T item : items.stream().sorted(Comparator.comparing(date)).toList()) {
                JsonNode v = extra(ai.get(id.apply(item)), f);
                if (v == null) {
                    continue;
                }
                String text = v.isArray()
                        ? java.util.stream.StreamSupport.stream(v.spliterator(), false)
                                .map(JsonNode::asText).collect(Collectors.joining(", "))
                        : v.asText();
                rows.add(new Row(List.of(date.apply(item).toString(), c.names().getOrDefault(student.apply(item), ""), text),
                        refType, id.apply(item)));
            }
            tables.add(new Table("extra_" + f.getFieldKey(), f.getLabel(), List.of("Date", "Student", f.getLabel()), rows));
        }
        return tables;
    }

    private List<AiFieldDefinition> fields(AiJobType type) {
        return fieldRepository.findByDeletedFalseOrderByJobTypeAscDisplayOrderAscCreatedAtAsc().stream()
                .filter(f -> f.getJobType() == type)
                .toList();
    }

    private static JsonNode extra(JsonNode result, AiFieldDefinition f) {
        if (result == null) {
            return null;
        }
        JsonNode v = result.path("extra").path(f.getFieldKey());
        return v.isMissingNode() || v.isNull() ? null : v;
    }

    // =================================================================== helpers

    private Map<UUID, JsonNode> results(Collection<UUID> targetIds) {
        Map<UUID, JsonNode> map = new HashMap<>();
        if (targetIds.isEmpty()) {
            return map;
        }
        for (AiResult r : resultRepository.findByTargetIdInAndCurrentTrue(targetIds)) {
            try {
                map.put(r.getTargetId(), objectMapper.readTree(r.getResultJson()));
            } catch (Exception ignored) {
                // a result that cannot be read is left out of the graphs
            }
        }
        return map;
    }

    private Chart line(String key, String title, String unit, Agg agg, boolean perItem, String drill,
                       List<Obs> obs, Ctx c) {
        Series s = SeriesBuilder.series(title, obs, agg, perItem, c.names());
        return new Chart(key, title, unit, "line", agg == Agg.SUM ? "sum" : "avg", perItem ? drill : "none",
                false, List.of(s));
    }

    private static Chart bar(String key, String title, Map<String, Long> counts) {
        List<Point> points = counts.entrySet().stream()
                .map(e -> new Point(e.getKey(), e.getValue(), null, null, null))
                .toList();
        return new Chart(key, title, "", "bar", "sum", "none", false, List.of(new Series(title, points)));
    }

    private static Map<String, Long> top(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static String avg(List<Obs> obs) {
        if (obs.isEmpty()) {
            return "—";
        }
        double v = obs.stream().mapToDouble(Obs::value).average().orElse(0);
        return String.valueOf(Math.round(v * 10) / 10.0);
    }

    static int words(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.strip().split("\\s+").length;
    }

    private static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max - 1) + "…" : text;
    }

    private static String studyTypeLabel(StudyType t) {
        return Arrays.stream(t.name().split("_"))
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private static String kindLabel(ReflectionType t) {
        return switch (t) {
            case TYPED -> "Typed";
            case VOICE -> "Voice";
            case BOTH -> "Typed and voice";
        };
    }
}
