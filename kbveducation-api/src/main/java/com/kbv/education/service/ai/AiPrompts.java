package com.kbv.education.service.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fixed instructions and reply formats sent with every job. Claude and Gemini get
 * exactly the same text (memo 17.10.2026, 1.4). The admin's own rules go in the
 * reference pack, which is sent right after these instructions.
 *
 * <p>Changing these strings changes the cached prompt prefix, so every request after a
 * deploy pays one cache write; keep them stable and free of dates or ids.</p>
 */
public final class AiPrompts {

    public static final List<String> RECOMMENDATIONS = List.of("approve", "look", "reject_recommended");
    public static final List<String> DEPTHS = List.of("touched", "practised", "secure");
    public static final List<String> PRACTICE_FLAGS = List.of("not_wholehearted", "ai_written", "low_effort", "ended_short");
    public static final List<String> REFLECTION_FLAGS = List.of("worrying", "disengaged", "overloaded");

    public static final String SCORE_NOT_FOUND = "not found";
    public static final String SCORE_NOT_APPLICABLE = "not applicable";

    private AiPrompts() {
    }

    public static final String PRACTICE_INSTRUCTIONS = """
            You help a teacher review the practice sessions their students log in a study-tracking app.
            Each session is the student's chat with an AI tutor, pasted by the student and split by the
            app into numbered turns (a turn is one message from the student or the tutor). Your notes are
            read only by the teacher. Students and parents never see them.

            The teacher makes every decision. You only give a recommendation and the facts behind it.

            What to return:
            - recommendation: "approve" (looks like a genuine study session), "look" (the teacher should
              read it themselves) or "reject_recommended" (probably not a genuine session).
            - trust_rating: a whole number from 1 to 10. 10 means clearly a real, serious study session;
              1 means almost certainly faked.
            - summary: about 50 to 60 words, written for the teacher. Use up to 200 words only when you are
              almost sure the session was faked, and then explain why.
            - past_paper_score: only when the study type is Past Paper Test Day. Copy the final score the
              AI tutor stated, exactly as written (for example "62/80"). Never mark the paper yourself.
              If the tutor stated no final score, return "not found". For every other study type return
              "not applicable".
            - engagement_rating: a whole number from 1 to 10. 10 means fully engaged (thinking, answering,
              asking questions); 1 means not engaged (going through the motions or ignoring the tutor).
            - engagement_reason: one line explaining the rating.
            - engagement_quote: a short quote that supports the rating, copied exactly from the transcript.
            - topics: every topic studied, each with how deeply: "touched" (mentioned or explained, not
              practised), "practised" (the student worked on it) or "secure" (the student answered
              correctly and without help, more than once). Use the topic names from the reference pack
              when one fits.
            - weak_topics: topics where the student struggled. An empty list if none.
            - flags: only the ones that apply, otherwise an empty list.
                "not_wholehearted": going through the motions, for example one real exchange and then nothing.
                "ai_written": the student's answers look AI-written, for example a second AI answered for them.
                "low_effort": very short or careless answers.
                "ended_short": the session stops much sooner than usual. Use the session length facts given
                with the session.
              Each flag needs a quote, a plain explanation, and turn_from / turn_to: the turn numbers where
              the quote appears.

            Rules for quotes: copy them character for character from the transcript, including spelling
            mistakes. Never shorten, fix or paraphrase a quote. The app checks every quote.

            Names in the text have been replaced: students by code-names such as "S-0007", other people
            by "[name]". Refer to the student by code-name only. Emails and phone numbers show as
            "[email]" and "[phone]".

            The teacher's reference pack follows. Use it for every judgement. These output rules always apply.
            """;

    public static final String REFLECTION_INSTRUCTIONS = """
            You help a teacher read the daily reflections their students submit in a study-tracking app.
            A reflection is the student's typed answers to the teacher's questions, a voice note turned
            into text by the app, or both. Your notes are read only by the teacher. Students and parents
            never see them.

            What to return:
            - summary: at most 40 words, written for the teacher.
            - flags: only the ones that apply, otherwise an empty list.
                "worrying": stress, sadness, problems at home, or anything about the student's safety or wellbeing.
                "disengaged": the student shows they have stopped caring.
                "overloaded": the student has too much work or no time.
              Each flag needs a quote copied exactly from the reflection and a plain explanation.
            - needs_attention: true when the teacher should look at this reflection soon, always true when
              there is a "worrying" flag; otherwise false.

            Rules for quotes: copy them character for character from the reflection, including spelling
            mistakes. Never shorten, fix or paraphrase a quote. The app checks every quote.

            Names in the text have been replaced: students by code-names such as "S-0007", other people
            by "[name]". Refer to the student by code-name only.

            The teacher's reference pack follows. Use it for every judgement. These output rules always apply.
            """;

    /** JSON schema for a practice-session reply. Ranges (1 to 10, turn numbers) are checked by the app. */
    public static Map<String, Object> practiceSchema() {
        Map<String, Object> flag = object(ordered(
                "type", enumOf(PRACTICE_FLAGS),
                "quote", string(),
                "explanation", string(),
                "turn_from", integer(),
                "turn_to", integer()));
        Map<String, Object> topic = object(ordered(
                "topic", string(),
                "depth", enumOf(DEPTHS)));
        return object(ordered(
                "recommendation", enumOf(RECOMMENDATIONS),
                "trust_rating", integer(),
                "summary", string(),
                "past_paper_score", string(),
                "engagement_rating", integer(),
                "engagement_reason", string(),
                "engagement_quote", string(),
                "topics", arrayOf(topic),
                "weak_topics", arrayOf(string()),
                "flags", arrayOf(flag)));
    }

    public static Map<String, Object> reflectionSchema() {
        Map<String, Object> flag = object(ordered(
                "type", enumOf(REFLECTION_FLAGS),
                "quote", string(),
                "explanation", string()));
        return object(ordered(
                "summary", string(),
                "flags", arrayOf(flag),
                "needs_attention", ordered("type", "boolean")));
    }

    /** Session facts the AI needs, then the numbered transcript. Lengths are counted by the app. */
    public static String practiceContent(String subject, String studyType, int durationMinutes, String date,
                                         String lengthFacts, String numberedTranscript, String partNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("Subject: ").append(subject).append('\n');
        sb.append("Study type: ").append(studyType).append('\n');
        sb.append("Duration logged by the student: ").append(durationMinutes).append(" minutes\n");
        sb.append("Date: ").append(date).append('\n');
        sb.append(lengthFacts).append('\n');
        if (partNote != null) {
            sb.append(partNote).append('\n');
        }
        sb.append("\nTranscript:\n\n").append(numberedTranscript);
        return sb.toString();
    }

    public static String reflectionContent(String date, String typedAnswers, String voiceTranscript) {
        StringBuilder sb = new StringBuilder();
        sb.append("Date: ").append(date).append("\n\n");
        if (typedAnswers != null && !typedAnswers.isBlank()) {
            sb.append("Typed answers:\n").append(typedAnswers).append("\n\n");
        }
        if (voiceTranscript != null && !voiceTranscript.isBlank()) {
            sb.append("Voice note (turned into text by the app):\n").append(voiceTranscript).append('\n');
        }
        return sb.toString();
    }

    // Schemas are built from insertion-ordered maps only: the serialized schema is part of the
    // request, and a key order that changed between runs would defeat prompt caching.

    /** An object whose properties are all required and which allows no other keys. */
    private static Map<String, Object> object(Map<String, Object> properties) {
        return ordered(
                "type", "object",
                "properties", properties,
                "required", List.copyOf(properties.keySet()),
                "additionalProperties", false);
    }

    private static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> enumOf(List<String> values) {
        return ordered("type", "string", "enum", values);
    }

    private static Map<String, Object> arrayOf(Map<String, Object> items) {
        return ordered("type", "array", "items", items);
    }

    private static Map<String, Object> string() {
        return ordered("type", "string");
    }

    private static Map<String, Object> integer() {
        return ordered("type", "integer");
    }
}
