package com.kbv.education.service.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a pasted transcript into numbered turns (memo 12.10.2026, section 6). A turn is
 * one message from the student or the AI tutor. The original transcript is never changed;
 * the turns are a separate, derived copy.
 *
 * <p>When the text has speaker labels ("Student:", "You said:", "ChatGPT:", ...), each label
 * starts a turn. Without labels the text is split on blank lines and every turn's speaker is
 * UNKNOWN. (The memo's "use the AI to split it once" for unlabelled text is not built yet.)</p>
 */
public final class TranscriptTurns {

    public static final String METHOD_LABELS = "LABELS";
    public static final String METHOD_PARAGRAPHS = "PARAGRAPHS";
    public static final String METHOD_NONE = "NONE";

    public static final String STUDENT = "STUDENT";
    public static final String TUTOR = "TUTOR";
    public static final String UNKNOWN = "UNKNOWN";

    private static final Set<String> STUDENT_LABELS = Set.of(
            "you said", "student", "me", "you", "user", "human", "i");

    private static final Pattern LABEL = Pattern.compile(
            "^[ \\t>*_#-]*(you said|chatgpt said|claude said|gemini said|copilot said|student|me|you|user|human"
                    + "|tutor|teacher|chatgpt|gpt|claude|gemini|copilot|assistant|ai|bot|model)[*_]*\\s*:\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private TranscriptTurns() {
    }

    public record Turn(int n, String speaker, String text) {
    }

    public record Split(List<Turn> turns, String method) {
    }

    public static Split split(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return new Split(List.of(), METHOD_NONE);
        }
        String normalised = transcript.replace("\r\n", "\n").replace('\r', '\n');
        List<Turn> labelled = byLabels(normalised);
        if (labelled.size() >= 2) {
            return new Split(labelled, METHOD_LABELS);
        }
        return new Split(byParagraphs(normalised), METHOD_PARAGRAPHS);
    }

    private static List<Turn> byLabels(String text) {
        List<Turn> turns = new ArrayList<>();
        String speaker = null;
        StringBuilder current = new StringBuilder();
        StringBuilder preamble = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            Matcher m = LABEL.matcher(line);
            if (m.matches()) {
                if (speaker != null) {
                    addTurn(turns, speaker, current.toString());
                }
                String label = m.group(1).toLowerCase(Locale.ROOT);
                speaker = STUDENT_LABELS.contains(label) ? STUDENT : TUTOR;
                current = new StringBuilder(m.group(2));
            } else if (speaker != null) {
                current.append('\n').append(line);
            } else {
                preamble.append(line).append('\n');
            }
        }
        if (speaker != null) {
            addTurn(turns, speaker, current.toString());
        }
        if (!turns.isEmpty() && !preamble.toString().isBlank()) {
            // Text before the first label is kept as its own turn so nothing is lost.
            List<Turn> withPreamble = new ArrayList<>();
            withPreamble.add(new Turn(1, UNKNOWN, preamble.toString().strip()));
            for (Turn t : turns) {
                withPreamble.add(new Turn(withPreamble.size() + 1, t.speaker(), t.text()));
            }
            return withPreamble;
        }
        return turns;
    }

    private static List<Turn> byParagraphs(String text) {
        List<Turn> turns = new ArrayList<>();
        for (String part : text.split("\\n\\s*\\n")) {
            if (!part.isBlank()) {
                turns.add(new Turn(turns.size() + 1, UNKNOWN, part.strip()));
            }
        }
        return turns;
    }

    private static void addTurn(List<Turn> turns, String speaker, String text) {
        String stripped = text.strip();
        if (!stripped.isEmpty()) {
            turns.add(new Turn(turns.size() + 1, speaker, stripped));
        }
    }

    /** The numbered transcript as the AI sees it: one "[Turn n] SPEAKER:" header per turn. */
    public static String numbered(List<Turn> turns) {
        StringBuilder sb = new StringBuilder();
        for (Turn t : turns) {
            sb.append("[Turn ").append(t.n()).append("] ").append(t.speaker()).append(":\n")
                    .append(t.text()).append("\n\n");
        }
        return sb.toString();
    }
}
