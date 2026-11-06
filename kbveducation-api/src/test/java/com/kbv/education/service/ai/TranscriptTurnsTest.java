package com.kbv.education.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Numbered turns: the basis of every "turns 14 to 16" pointer on the review card. */
class TranscriptTurnsTest {

    @Test
    void splitsOnSpeakerLabelsAndKeepsMultiLineMessages() {
        String transcript = """
                Student: Give me question 1
                Tutor: What is 12 + 16 + 16?
                It is about CO2.
                Student: 44
                """;

        TranscriptTurns.Split split = TranscriptTurns.split(transcript);

        assertThat(split.method()).isEqualTo(TranscriptTurns.METHOD_LABELS);
        assertThat(split.turns()).extracting(TranscriptTurns.Turn::speaker)
                .containsExactly("STUDENT", "TUTOR", "STUDENT");
        assertThat(split.turns().get(1).text()).isEqualTo("What is 12 + 16 + 16?\nIt is about CO2.");
        assertThat(split.turns()).extracting(TranscriptTurns.Turn::n).containsExactly(1, 2, 3);
    }

    @Test
    void understandsChatGptCopyFormatWithLabelOnItsOwnLine() {
        String transcript = "You said:\nhelp me with moles\nChatGPT said:\nSure. What is a mole?\nYou said:\n6.02 x 10^23";

        List<TranscriptTurns.Turn> turns = TranscriptTurns.split(transcript).turns();

        assertThat(turns).extracting(TranscriptTurns.Turn::speaker).containsExactly("STUDENT", "TUTOR", "STUDENT");
        assertThat(turns.get(0).text()).isEqualTo("help me with moles");
    }

    @Test
    void keepsTextBeforeTheFirstLabelAsItsOwnTurn() {
        List<TranscriptTurns.Turn> turns =
                TranscriptTurns.split("Chemistry session\nStudent: hi\nTutor: hello").turns();

        assertThat(turns).hasSize(3);
        assertThat(turns.get(0).speaker()).isEqualTo("UNKNOWN");
        assertThat(turns.get(0).text()).isEqualTo("Chemistry session");
    }

    @Test
    void withoutLabelsSplitsOnBlankLines() {
        TranscriptTurns.Split split = TranscriptTurns.split("can you help with osmosis\n\nOf course!\n\nyes");

        assertThat(split.method()).isEqualTo(TranscriptTurns.METHOD_PARAGRAPHS);
        assertThat(split.turns()).hasSize(3).allMatch(t -> t.speaker().equals("UNKNOWN"));
    }

    @Test
    void emptyTranscriptHasNoTurns() {
        assertThat(TranscriptTurns.split(null).method()).isEqualTo(TranscriptTurns.METHOD_NONE);
        assertThat(TranscriptTurns.split("   ").turns()).isEmpty();
    }

    @Test
    void numberedTextMatchesTheTurnNumbers() {
        String numbered = TranscriptTurns.numbered(TranscriptTurns.split("Student: a\nTutor: b").turns());

        assertThat(numbered).contains("[Turn 1] STUDENT:\na").contains("[Turn 2] TUTOR:\nb");
    }
}
