package com.kbv.education.entity;

import com.kbv.education.entity.enums.AiJobStatus;
import com.kbv.education.entity.enums.ReflectionType;
import com.kbv.education.entity.enums.TranscriptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A student's daily reflection (at most one per day). The optional audio file is
 * stored as-is; NO speech-to-text is performed — the file is kept for future AI
 * transcription. Typed answers live in {@link ReflectionAnswer}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "reflection_entries")
public class ReflectionEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "reflection_date", nullable = false)
    private LocalDate reflectionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reflection_type", nullable = false, length = 20)
    private ReflectionType reflectionType = ReflectionType.TYPED;

    @Column(name = "audio_file_name", length = 255)
    private String audioFileName;

    @Column(name = "audio_stored_name", length = 255)
    private String audioStoredName;

    @Column(name = "audio_file_type", length = 100)
    private String audioFileType;

    @Column(name = "audio_file_size")
    private Long audioFileSize;

    /**
     * Transcript of the audio. Always null under the manual (no-AI) path; a
     * future {@code ReflectionTranscriptionService} implementation fills this
     * in without any schema change.
     */
    @Column(name = "transcript", columnDefinition = "text")
    private String transcript;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    /** Voice-note transcription state; a voice note is transcribed at most once. */
    @Enumerated(EnumType.STRING)
    @Column(name = "transcript_status", nullable = false, length = 20)
    private TranscriptStatus transcriptStatus = TranscriptStatus.NONE;

    @Column(name = "transcript_model", length = 100)
    private String transcriptModel;

    @Column(name = "transcribed_at")
    private Instant transcribedAt;

    // Copy of the current AI headline (see AiResult) for the admin lists.

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", length = 20)
    private AiJobStatus aiStatus;

    @Column(name = "ai_flag_count")
    private Integer aiFlagCount;

    @Column(name = "ai_needs_attention", nullable = false)
    private boolean aiNeedsAttention = false;
}
