package com.kbv.education.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kbv.education.entity.AiFieldDefinition;
import com.kbv.education.entity.enums.AiJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks every field of an AI reply (memo 12.10.2026, section 3). A reply that fails any
 * check counts as "not in the fixed format": the engine retries once, then uses the backup.
 *
 * <p>Quotes are checked word for word against the exact text the AI was given (names
 * already removed). A quote that is not found keeps its flag, marked
 * {@code quote_verified: false}, so the admin sees "quote not verified".</p>
 */
@Component
@RequiredArgsConstructor
public class AiResultValidator {

    private final ObjectMapper objectMapper;

    /** The checked reply plus the headline values copied into columns. */
    public record Validated(ObjectNode json, String recommendation, Integer trustRating,
                            Integer engagementRating, int flagCount, boolean needsAttention) {
    }

    public static final class InvalidResultException extends Exception {
        public InvalidResultException(String message) {
            super(message);
        }
    }

    public Validated validate(AiJobType type, String text, String sourceText, int turnCount)
            throws InvalidResultException {
        return validate(type, text, sourceText, turnCount, List.of());
    }

    /** Also checks the admin's own fields in the reply's {@code extra} object. */
    public Validated validate(AiJobType type, String text, String sourceText, int turnCount,
                              List<AiFieldDefinition> customFields) throws InvalidResultException {
        Validated v = validateBuiltIn(type, text, sourceText, turnCount);
        checkExtra(v.json(), customFields);
        return v;
    }

    private void checkExtra(ObjectNode root, List<AiFieldDefinition> fields) throws InvalidResultException {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        JsonNode extra = root.path("extra");
        if (!extra.isObject()) {
            throw new InvalidResultException("extra is missing");
        }
        List<String> problems = new ArrayList<>();
        for (AiFieldDefinition f : fields) {
            JsonNode v = extra.path(f.getFieldKey());
            boolean ok = switch (f.getFieldType()) {
                case NUMBER -> v.isNumber();
                case RATING -> v.isInt() && v.asInt() >= 1 && v.asInt() <= 10;
                case YES_NO -> v.isBoolean();
                case SHORT_TEXT -> v.isTextual();
                case LIST -> v.isArray() && allText(v);
            };
            if (!ok) {
                problems.add("extra." + f.getFieldKey() + " is not a valid " + f.getFieldType());
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidResultException(String.join("; ", problems));
        }
    }

    private static boolean allText(JsonNode array) {
        for (JsonNode item : array) {
            if (!item.isTextual()) {
                return false;
            }
        }
        return true;
    }

    private Validated validateBuiltIn(AiJobType type, String text, String sourceText, int turnCount)
            throws InvalidResultException {
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(text);
        } catch (Exception e) {
            throw new InvalidResultException("Reply is not valid JSON");
        }
        if (!(parsed instanceof ObjectNode root)) {
            throw new InvalidResultException("Reply is not a JSON object");
        }
        String normalisedSource = normalise(sourceText);
        return type == AiJobType.PRACTICE_REVIEW
                ? validatePractice(root, normalisedSource, turnCount)
                : validateReflection(root, normalisedSource);
    }

    private Validated validatePractice(ObjectNode root, String source, int turnCount) throws InvalidResultException {
        List<String> problems = new ArrayList<>();
        String recommendation = enumField(root, "recommendation", Set.copyOf(AiPrompts.RECOMMENDATIONS), problems);
        Integer trust = rating(root, "trust_rating", problems);
        requireText(root, "summary", problems);
        requireText(root, "past_paper_score", problems);
        Integer engagement = rating(root, "engagement_rating", problems);
        requireText(root, "engagement_reason", problems);
        requireText(root, "engagement_quote", problems);

        ArrayNode topics = array(root, "topics", problems);
        if (topics != null) {
            for (JsonNode t : topics) {
                if (!t.path("topic").isTextual() || !AiPrompts.DEPTHS.contains(t.path("depth").asText())) {
                    problems.add("a topic is missing its name or has an unknown depth");
                }
            }
        }
        ArrayNode weak = array(root, "weak_topics", problems);
        if (weak != null) {
            for (JsonNode w : weak) {
                if (!w.isTextual()) {
                    problems.add("weak_topics must be text");
                }
            }
        }
        ArrayNode flags = array(root, "flags", problems);
        if (flags != null) {
            for (JsonNode f : flags) {
                if (!AiPrompts.PRACTICE_FLAGS.contains(f.path("type").asText())
                        || !f.path("quote").isTextual() || !f.path("explanation").isTextual()) {
                    problems.add("a flag has an unknown type or is missing its quote or explanation");
                    continue;
                }
                int from = f.path("turn_from").asInt(-1);
                int to = f.path("turn_to").asInt(-1);
                if (!f.path("turn_from").isInt() || !f.path("turn_to").isInt()
                        || from < 1 || to < from || (turnCount > 0 && to > turnCount)) {
                    problems.add("a flag points to turns that do not exist (" + from + " to " + to + ")");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidResultException(String.join("; ", problems));
        }

        markQuotes(flags, source);
        root.put("engagement_quote_verified", contains(source, root.path("engagement_quote").asText()));
        return new Validated(root, recommendation, trust, engagement, flags.size(), false);
    }

    private Validated validateReflection(ObjectNode root, String source) throws InvalidResultException {
        List<String> problems = new ArrayList<>();
        requireText(root, "summary", problems);
        if (!root.path("needs_attention").isBoolean()) {
            problems.add("needs_attention must be true or false");
        }
        ArrayNode flags = array(root, "flags", problems);
        if (flags != null) {
            for (JsonNode f : flags) {
                if (!AiPrompts.REFLECTION_FLAGS.contains(f.path("type").asText())
                        || !f.path("quote").isTextual() || !f.path("explanation").isTextual()) {
                    problems.add("a flag has an unknown type or is missing its quote or explanation");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidResultException(String.join("; ", problems));
        }
        markQuotes(flags, source);
        boolean worrying = false;
        for (JsonNode f : flags) {
            worrying |= "worrying".equals(f.path("type").asText());
        }
        // A worrying flag always needs the admin, whatever the AI said.
        boolean needsAttention = root.path("needs_attention").asBoolean() || worrying;
        root.put("needs_attention", needsAttention);
        return new Validated(root, null, null, null, flags.size(), needsAttention);
    }

    private void markQuotes(ArrayNode flags, String source) {
        for (JsonNode f : flags) {
            ((ObjectNode) f).put("quote_verified", contains(source, f.path("quote").asText()));
        }
    }

    private boolean contains(String normalisedSource, String quote) {
        String q = normalise(quote);
        return !q.isEmpty() && normalisedSource.contains(q);
    }

    /** Word for word, but ignoring differences in spacing, line breaks and curly vs straight quotes. */
    static String normalise(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replaceAll("\\s+", " ")
                .strip();
    }

    private String enumField(ObjectNode root, String field, Set<String> allowed, List<String> problems) {
        String value = root.path(field).asText(null);
        if (value == null || !allowed.contains(value)) {
            problems.add(field + " must be one of " + allowed);
        }
        return value;
    }

    private Integer rating(ObjectNode root, String field, List<String> problems) {
        JsonNode node = root.path(field);
        if (!node.isInt() || node.asInt() < 1 || node.asInt() > 10) {
            problems.add(field + " must be a whole number from 1 to 10");
            return null;
        }
        return node.asInt();
    }

    private void requireText(ObjectNode root, String field, List<String> problems) {
        if (!root.path(field).isTextual() || root.path(field).asText().isBlank()) {
            problems.add(field + " is missing");
        }
    }

    private ArrayNode array(ObjectNode root, String field, List<String> problems) {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            problems.add(field + " must be a list");
            return null;
        }
        return (ArrayNode) node;
    }
}
