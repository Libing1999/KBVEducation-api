package com.kbv.education.service.ai;

import com.kbv.education.dto.ai.AiFieldResponse;
import com.kbv.education.dto.ai.AiFieldResponse.AiFieldList;
import com.kbv.education.entity.AiFieldDefinition;
import com.kbv.education.entity.enums.AiFieldType;
import com.kbv.education.entity.enums.AiJobType;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.AiFieldDefinitionRepository;
import com.kbv.education.repository.PracticeSessionRepository;
import com.kbv.education.repository.ReflectionEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The fields the AI returns (memo 17.10.2026, 2.1): the built-in ones, which are fixed, and the
 * admin's own, which can be added at any time without a code change. A new field applies from
 * the next run; older items can be analysed again to fill it in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiFieldService {

    /** Most items one "analyse again" request may send, to keep a mistaken date range cheap. */
    public static final int MAX_REANALYSE = 300;

    private static final Set<String> RESERVED = Set.of(
            "recommendation", "trust_rating", "summary", "past_paper_score", "engagement_rating",
            "engagement_reason", "engagement_quote", "topics", "weak_topics", "flags", "needs_attention", "extra");

    private final AiFieldDefinitionRepository repository;
    private final PracticeSessionRepository practiceRepository;
    private final ReflectionEntryRepository reflectionRepository;
    private final AiEngine engine;

    @Transactional(readOnly = true)
    public AiFieldList list() {
        List<AiFieldDefinition> custom = repository.findByDeletedFalseOrderByJobTypeAscDisplayOrderAscCreatedAtAsc();
        return new AiFieldList(
                concat(builtInPractice(), custom, AiJobType.PRACTICE_REVIEW),
                concat(builtInReflection(), custom, AiJobType.REFLECTION_REVIEW));
    }

    @Transactional
    public AiFieldResponse create(AiFieldResponse.CreateAiFieldRequest r) {
        String key = keyFor(r.label());
        if (RESERVED.contains(key)) {
            throw new BadRequestException("\"" + r.label() + "\" is already a built-in field");
        }
        String unique = key;
        for (int i = 2; repository.existsByJobTypeAndFieldKey(r.jobType(), unique); i++) {
            unique = key + "_" + i;
        }
        AiFieldDefinition f = new AiFieldDefinition();
        f.setJobType(r.jobType());
        f.setFieldKey(unique);
        f.setLabel(r.label().strip());
        f.setDescription(r.description().strip());
        f.setFieldType(r.fieldType());
        f.setActive(true);
        f.setDisplayOrder((int) repository.count());
        AiFieldDefinition saved = repository.save(f);
        log.info("Added AI field {} ({}) for {}", saved.getFieldKey(), saved.getFieldType(), saved.getJobType());
        return toResponse(saved);
    }

    /** Label and description can change; the key and type never do, so saved results keep their meaning. */
    @Transactional
    public AiFieldResponse update(UUID id, AiFieldResponse.UpdateAiFieldRequest r) {
        AiFieldDefinition f = repository.findById(id)
                .filter(x -> !x.isDeleted())
                .orElseThrow(() -> ResourceNotFoundException.of("AI field", id));
        f.setLabel(r.label().strip());
        f.setDescription(r.description().strip());
        f.setActive(r.active());
        return toResponse(repository.save(f));
    }

    /** Sends every item submitted in the date range to the AI again (normal calls). Returns how many. */
    @Transactional(readOnly = true)
    public int reanalyse(AiFieldResponse.ReanalyseRequest r) {
        if (r.to().isBefore(r.from())) {
            throw new BadRequestException("The end date is before the start date");
        }
        List<UUID> ids = r.jobType() == AiJobType.PRACTICE_REVIEW
                ? practiceRepository.findIdsByStudyDateBetween(r.from(), r.to())
                : reflectionRepository.findIdsByReflectionDateBetween(r.from(), r.to());
        if (ids.size() > MAX_REANALYSE) {
            throw new BusinessRuleException("That range has " + ids.size() + " items; choose a shorter range (at most "
                    + MAX_REANALYSE + " at a time)");
        }
        engine.startReanalyseMany(r.jobType(), ids);
        log.info("Re-analysing {} {} item(s) from {} to {}", ids.size(), r.jobType(), r.from(), r.to());
        return ids.size();
    }

    // --- helpers -----------------------------------------------------------

    /** "Used past notes?" becomes "used_past_notes". */
    static String keyFor(String label) {
        String ascii = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String key = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (key.isEmpty()) {
            throw new BadRequestException("The name needs at least one letter or digit");
        }
        if (Character.isDigit(key.charAt(0))) {
            key = "f_" + key;
        }
        return key.length() > 50 ? key.substring(0, 50) : key;
    }

    private List<AiFieldResponse> concat(List<AiFieldResponse> builtIn, List<AiFieldDefinition> custom, AiJobType type) {
        return java.util.stream.Stream.concat(builtIn.stream(),
                        custom.stream().filter(f -> f.getJobType() == type).map(this::toResponse))
                .toList();
    }

    private AiFieldResponse toResponse(AiFieldDefinition f) {
        return new AiFieldResponse(f.getId(), f.getJobType(), f.getFieldKey(), f.getLabel(), f.getDescription(),
                f.getFieldType().name(), false, f.isActive(), f.getCreatedAt());
    }

    private static AiFieldResponse builtIn(AiJobType type, String key, String label, String fieldType, String description) {
        return new AiFieldResponse(null, type, key, label, description, fieldType, true, true, null);
    }

    private static List<AiFieldResponse> builtInPractice() {
        AiJobType t = AiJobType.PRACTICE_REVIEW;
        return List.of(
                builtIn(t, "recommendation", "Recommendation", "SHORT_TEXT", "approve, look or reject recommended"),
                builtIn(t, "trust_rating", "Trust rating", AiFieldType.RATING.name(), "10 = clearly a real, serious session; 1 = almost certainly faked"),
                builtIn(t, "summary", "Summary", "SHORT_TEXT", "About 50 to 60 words for you"),
                builtIn(t, "past_paper_score", "Past paper score", "SHORT_TEXT", "Final score stated by the tutor on Past Paper Test Days"),
                builtIn(t, "engagement_rating", "Engagement", AiFieldType.RATING.name(), "10 = fully engaged; 1 = not engaged, with a reason and a quote"),
                builtIn(t, "topics", "Topics studied", "LIST", "Each topic with its depth: touched, practised or secure"),
                builtIn(t, "weak_topics", "Weak topics", "LIST", "Topics where the student struggled"),
                builtIn(t, "flags", "Flags", "LIST", "Not wholehearted, AI-written, low effort, ended short; each with a quote"));
    }

    private static List<AiFieldResponse> builtInReflection() {
        AiJobType t = AiJobType.REFLECTION_REVIEW;
        return List.of(
                builtIn(t, "summary", "Summary", "SHORT_TEXT", "At most 40 words"),
                builtIn(t, "flags", "Flags", "LIST", "Worrying, disengaged, overloaded; each with a quote"),
                builtIn(t, "needs_attention", "Needs my attention", AiFieldType.YES_NO.name(), "Always yes with a worrying flag"));
    }
}
