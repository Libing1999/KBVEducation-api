package com.kbv.education.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.entity.enums.AiJobType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every field of an AI reply is checked; quotes are checked word for word. */
class AiResultValidatorTest {

    private final AiResultValidator validator = new AiResultValidator(new ObjectMapper());

    private static final String SOURCE = "Give me question 1\nWhat is 12 + 16 + 16?\nidk\nok next";

    private static String practice(String recommendation, int trust, String flags) {
        return """
                {"recommendation":"%s","trust_rating":%d,"summary":"S-0003 worked through one question.",
                 "past_paper_score":"not applicable","engagement_rating":3,"engagement_reason":"Short answers",
                 "engagement_quote":"idk","topics":[{"topic":"Moles","depth":"touched"}],
                 "weak_topics":["Moles"],"flags":%s}
                """.formatted(recommendation, trust, flags);
    }

    @Test
    void acceptsAValidReplyAndMarksQuotes() throws Exception {
        String flags = """
                [{"type":"low_effort","quote":"ok   next","explanation":"One-word replies","turn_from":3,"turn_to":4},
                 {"type":"ended_short","quote":"I give up","explanation":"Stops early","turn_from":4,"turn_to":4}]""";

        AiResultValidator.Validated v =
                validator.validate(AiJobType.PRACTICE_REVIEW, practice("look", 4, flags), SOURCE, 4);

        assertThat(v.recommendation()).isEqualTo("look");
        assertThat(v.trustRating()).isEqualTo(4);
        assertThat(v.flagCount()).isEqualTo(2);
        // Spacing differences are ignored; a quote not in the text keeps its flag but is marked.
        assertThat(v.json().path("flags").get(0).path("quote_verified").asBoolean()).isTrue();
        assertThat(v.json().path("flags").get(1).path("quote_verified").asBoolean()).isFalse();
        assertThat(v.json().path("engagement_quote_verified").asBoolean()).isTrue();
    }

    @Test
    void rejectsAnOutOfRangeRating() {
        assertThatThrownBy(() -> validator.validate(AiJobType.PRACTICE_REVIEW, practice("approve", 11, "[]"), SOURCE, 4))
                .isInstanceOf(AiResultValidator.InvalidResultException.class)
                .hasMessageContaining("trust_rating");
    }

    @Test
    void rejectsAnUnknownRecommendation() {
        assertThatThrownBy(() -> validator.validate(AiJobType.PRACTICE_REVIEW, practice("reject", 2, "[]"), SOURCE, 4))
                .isInstanceOf(AiResultValidator.InvalidResultException.class);
    }

    @Test
    void rejectsAFlagPointingPastTheLastTurn() {
        String flags = """
                [{"type":"low_effort","quote":"idk","explanation":"x","turn_from":3,"turn_to":9}]""";
        assertThatThrownBy(() -> validator.validate(AiJobType.PRACTICE_REVIEW, practice("look", 5, flags), SOURCE, 4))
                .isInstanceOf(AiResultValidator.InvalidResultException.class)
                .hasMessageContaining("turns");
    }

    @Test
    void rejectsSomethingThatIsNotJson() {
        assertThatThrownBy(() -> validator.validate(AiJobType.PRACTICE_REVIEW, "Sure! Here is my review", SOURCE, 4))
                .isInstanceOf(AiResultValidator.InvalidResultException.class);
    }

    @Test
    void aWorryingReflectionAlwaysNeedsAttention() throws Exception {
        String reply = """
                {"summary":"Tired before tests.","needs_attention":false,
                 "flags":[{"type":"worrying","quote":"I can’t sleep","explanation":"Stress before tests"}]}""";

        AiResultValidator.Validated v =
                validator.validate(AiJobType.REFLECTION_REVIEW, reply, "I can't sleep before tests", 0);

        assertThat(v.needsAttention()).isTrue();
        // Curly and straight apostrophes count as the same character.
        assertThat(v.json().path("flags").get(0).path("quote_verified").asBoolean()).isTrue();
    }

    @Test
    void schemaKeyOrderIsStableSoThePromptCacheKeepsWorking() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String first = mapper.writeValueAsString(AiPrompts.practiceSchema());
        String second = mapper.writeValueAsString(AiPrompts.practiceSchema());

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("{\"type\":\"object\",\"properties\":{\"recommendation\"");
    }
}
