package com.kbv.education.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.dto.ai.MorningDashboardResponse;
import com.kbv.education.dto.ai.MorningDashboardResponse.NeedsMe;
import com.kbv.education.dto.ai.MorningDashboardResponse.Numbers;
import com.kbv.education.dto.ai.MorningDashboardResponse.ReflectionItem;
import com.kbv.education.dto.ai.MorningDashboardResponse.Student;
import com.kbv.education.dto.ai.MorningDashboardResponse.StudentReflections;
import com.kbv.education.dto.ai.MorningDashboardResponse.SuspiciousSession;
import com.kbv.education.entity.AiResult;
import com.kbv.education.entity.AiSettings;
import com.kbv.education.entity.PracticeSession;
import com.kbv.education.entity.ReflectionEntry;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.AiJobStatus;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.repository.AiResultRepository;
import com.kbv.education.repository.PracticeSessionRepository;
import com.kbv.education.repository.ReflectionEntryRepository;
import com.kbv.education.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds the morning dashboard (memo 12.10.2026, 4.5) for one day, by default yesterday in
 * the AI time zone (Dubai). "Uploaded yesterday" means submitted during that calendar day.
 *
 * <p>The "sudden drop compared with their own usual" reason is not included yet: it needs
 * the client's definition (which measure, how big a drop).</p>
 */
@Service
@RequiredArgsConstructor
public class MorningDashboardService {

    private final UserRepository userRepository;
    private final PracticeSessionRepository practiceRepository;
    private final ReflectionEntryRepository reflectionRepository;
    private final AiResultRepository resultRepository;
    private final AiSettingsService settingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public MorningDashboardResponse build(LocalDate requestedDate) {
        AiSettings settings = settingsService.getActive();
        ZoneId zone = ZoneId.of(settings.getRunTimezone());
        LocalDate date = requestedDate != null ? requestedDate : LocalDate.now(zone).minusDays(1);
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();

        List<User> students = userRepository.findByRole_NameAndStatusAndDeletedFalse(RoleType.STUDENT, UserStatus.ACTIVE);
        List<PracticeSession> sessions =
                practiceRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDeletedFalse(from, to);
        List<ReflectionEntry> reflections =
                reflectionRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDeletedFalse(from, to);
        Map<UUID, String> summaries = summaries(Stream.concat(
                sessions.stream().map(PracticeSession::getId),
                reflections.stream().map(ReflectionEntry::getId)).toList());

        // 1. Numbers (always complete: the app's own data)
        Set<UUID> practiced = sessions.stream().map(s -> s.getStudent().getId()).collect(Collectors.toSet());
        Set<UUID> reflected = reflections.stream().map(r -> r.getStudent().getId()).collect(Collectors.toSet());
        List<Student> nothing = students.stream()
                .filter(u -> !practiced.contains(u.getId()) && !reflected.contains(u.getId()))
                .sorted(Comparator.comparing(User::getFullName))
                .map(this::student)
                .toList();
        Numbers numbers = new Numbers(students.size(), practiced.size(), sessions.size(), reflected.size(), nothing);

        // 2. Suspicious sessions: any flag or a red rating, lowest rating first
        List<SuspiciousSession> suspicious = sessions.stream()
                .filter(s -> isSuspicious(s, settings))
                .sorted(Comparator.comparing(PracticeSession::getAiTrustRating,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(s -> new SuspiciousSession(s.getId(), student(s.getStudent()), s.getSubject(), s.getStudyType(),
                        s.getAiStatus(), s.getAiTrustRating(), s.getAiFlagCount(), summaries.get(s.getId())))
                .toList();

        // 3. Reflections grouped by student, flagged groups first
        Map<UUID, List<ReflectionEntry>> byStudent = new LinkedHashMap<>();
        reflections.stream()
                .sorted(Comparator.comparing(ReflectionEntry::getSubmittedAt))
                .forEach(r -> byStudent.computeIfAbsent(r.getStudent().getId(), k -> new ArrayList<>()).add(r));
        List<StudentReflections> grouped = byStudent.values().stream()
                .map(list -> new StudentReflections(student(list.get(0).getStudent()), list.stream()
                        .map(r -> new ReflectionItem(r.getId(), r.getReflectionType(), r.getAiStatus(),
                                r.getAiFlagCount(), r.isAiNeedsAttention(), summaries.get(r.getId()),
                                r.getAudioStoredName() != null))
                        .toList()))
                .sorted(Comparator.comparing((StudentReflections g) -> !isFlagged(g))
                        .thenComparing(g -> g.student().name()))
                .toList();

        // 4. Needs me
        List<NeedsMe> needsMe = needsMe(students, sessions, reflections, settings, zone, date);

        return new MorningDashboardResponse(date, zone.getId(), numbers, suspicious, grouped, needsMe);
    }

    private List<NeedsMe> needsMe(List<User> students, List<PracticeSession> sessions,
                                  List<ReflectionEntry> reflections, AiSettings settings, ZoneId zone, LocalDate date) {
        Map<UUID, List<String>> reasons = new LinkedHashMap<>();
        for (ReflectionEntry r : reflections) {
            if (r.getAiFlagCount() != null && r.getAiFlagCount() > 0) {
                add(reasons, r.getStudent().getId(), "Flagged reflection");
            } else if (r.isAiNeedsAttention()) {
                add(reasons, r.getStudent().getId(), "Reflection needs your attention");
            }
            if (r.getAiStatus() == AiJobStatus.NOT_ANALYSED) {
                add(reasons, r.getStudent().getId(), "Reflection not analysed");
            }
        }
        for (PracticeSession s : sessions) {
            if (isSuspicious(s, settings)) {
                add(reasons, s.getStudent().getId(), "Suspicious practice session (" + s.getSubject() + ")");
            }
            if (s.getAiStatus() == AiJobStatus.NOT_ANALYSED) {
                add(reasons, s.getStudent().getId(), "Practice session not analysed");
            }
        }
        // No practice session for N days in a row (default 2), counted up to and including the dashboard day.
        Map<UUID, Instant> last = new HashMap<>();
        for (Object[] row : practiceRepository.lastSubmissionPerStudent()) {
            last.put((UUID) row[0], (Instant) row[1]);
        }
        int days = settings.getNeedsMeDaysWithoutSession();
        for (User u : students) {
            Instant latest = last.get(u.getId());
            LocalDate lastDay = latest == null ? null : latest.atZone(zone).toLocalDate();
            if (lastDay == null || !lastDay.isAfter(date.minusDays(days))) {
                add(reasons, u.getId(), lastDay == null
                        ? "No practice session yet"
                        : "No practice session for " + days + "+ days (last: " + lastDay + ")");
            }
        }
        Map<UUID, User> byId = students.stream().collect(Collectors.toMap(User::getId, u -> u));
        List<NeedsMe> result = new ArrayList<>();
        reasons.forEach((id, list) -> {
            User u = byId.get(id);
            if (u != null) {
                result.add(new NeedsMe(student(u), list));
            }
        });
        result.sort(Comparator.comparing((NeedsMe n) -> -n.reasons().size()).thenComparing(n -> n.student().name()));
        return result;
    }

    private boolean isSuspicious(PracticeSession s, AiSettings settings) {
        boolean flagged = s.getAiFlagCount() != null && s.getAiFlagCount() > 0;
        boolean red = s.getAiTrustRating() != null && s.getAiTrustRating() < settings.getAmberMinRating();
        return flagged || red;
    }

    private boolean isFlagged(StudentReflections g) {
        return g.items().stream().anyMatch(i -> (i.flagCount() != null && i.flagCount() > 0) || i.needsAttention());
    }

    private Map<UUID, String> summaries(List<UUID> targetIds) {
        Map<UUID, String> map = new HashMap<>();
        if (targetIds.isEmpty()) {
            return map;
        }
        for (AiResult r : resultRepository.findByTargetIdInAndCurrentTrue(targetIds)) {
            try {
                JsonNode json = objectMapper.readTree(r.getResultJson());
                map.put(r.getTargetId(), json.path("summary").asText(null));
            } catch (Exception ignored) {
                // a result that cannot be read simply shows no summary
            }
        }
        return map;
    }

    private static void add(Map<UUID, List<String>> reasons, UUID studentId, String reason) {
        List<String> list = reasons.computeIfAbsent(studentId, k -> new ArrayList<>());
        if (!list.contains(reason)) {
            list.add(reason);
        }
    }

    private Student student(User u) {
        return new Student(u.getId(), u.getFullName());
    }
}
