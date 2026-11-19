package com.kbv.education.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.entity.AiFieldDefinition;
import com.kbv.education.entity.enums.AiFieldType;
import com.kbv.education.entity.enums.AiJobType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The admin's own AI fields (memo 17.10.2026, 2.1): reply format, checks and keys. */
class AiCustomFieldsTest {

    private final AiResultValidator validator = new AiResultValidator(new ObjectMapper());

    private static AiFieldDefinition field(String key, AiFieldType type) {
        AiFieldDefinition f = new AiFieldDefinition();
        f.setJobType(AiJobType.REFLECTION_REVIEW);
        f.setFieldKey(key);
        f.setLabel(key);
        f.setDescription("what to look for");
        f.setFieldType(type);
        return f;
    }

    private static final List<AiFieldDefinition> FIELDS = List.of(
            field("sleep_hours", AiFieldType.NUMBER),
            field("mood", AiFieldType.RATING),
            field("mentions_exam", AiFieldType.YES_NO),
            field("main_worry", AiFieldType.SHORT_TEXT),
            field("subjects_mentioned", AiFieldType.LIST));

    private static String reflection(String extra) {
        return """
                {"summary":"Tired before tests.","needs_attention":false,"flags":[],"extra":%s}
                """.formatted(extra);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replyFormatGetsAnExtraObjectWithEveryFieldRequired() {
        Map<String, Object> schema = AiPrompts.reflectionSchema(FIELDS);
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        Map<String, Object> extra = (Map<String, Object>) props.get("extra");

        assertThat((List<String>) schema.get("required")).contains("extra", "summary");
        assertThat((List<String>) extra.get("required"))
                .containsExactly("sleep_hours", "mood", "mentions_exam", "main_worry", "subjects_mentioned");
        assertThat(((Map<String, Object>) ((Map<String, Object>) extra.get("properties")).get("mood")).get("type"))
                .isEqualTo("integer");
    }

    @Test
    void withoutCustomFieldsTheFormatIsUnchanged() {
        assertThat(AiPrompts.reflectionSchema(List.of())).isEqualTo(AiPrompts.reflectionSchema());
    }

    @Test
    void acceptsCorrectlyTypedValues() throws Exception {
        String reply = reflection("""
                {"sleep_hours":5.5,"mood":3,"mentions_exam":true,"main_worry":"Chemistry test","subjects_mentioned":["Chemistry"]}""");

        AiResultValidator.Validated v =
                validator.validate(AiJobType.REFLECTION_REVIEW, reply, "I am tired", 0, FIELDS);

        assertThat(v.json().path("extra").path("mood").asInt()).isEqualTo(3);
    }

    @Test
    void rejectsAWronglyTypedValue() {
        String reply = reflection("""
                {"sleep_hours":"five","mood":11,"mentions_exam":"yes","main_worry":"x","subjects_mentioned":[1]}""");

        assertThatThrownBy(() -> validator.validate(AiJobType.REFLECTION_REVIEW, reply, "x", 0, FIELDS))
                .isInstanceOf(AiResultValidator.InvalidResultException.class)
                .hasMessageContaining("extra.sleep_hours")
                .hasMessageContaining("extra.mood")
                .hasMessageContaining("extra.mentions_exam")
                .hasMessageContaining("extra.subjects_mentioned");
    }

    @Test
    void rejectsAMissingExtraObject() {
        String reply = """
                {"summary":"ok","needs_attention":false,"flags":[]}""";

        assertThatThrownBy(() -> validator.validate(AiJobType.REFLECTION_REVIEW, reply, "x", 0, FIELDS))
                .isInstanceOf(AiResultValidator.InvalidResultException.class)
                .hasMessageContaining("extra");
    }

    @Test
    void fieldNamesBecomeSafeKeys() {
        assertThat(AiFieldService.keyFor("Used past notes?")).isEqualTo("used_past_notes");
        assertThat(AiFieldService.keyFor("  Café — Mood!  ")).isEqualTo("cafe_mood");
        assertThat(AiFieldService.keyFor("3 hardest topics")).isEqualTo("f_3_hardest_topics");
    }
}
