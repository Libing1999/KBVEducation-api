package com.kbv.education.service.ai;

import java.util.UUID;

/**
 * Extension point for turning an uploaded reflection audio file into text.
 *
 * <p>Phase 3 ships {@link ManualReflectionTranscriptionService}, which performs
 * NO transcription — the audio is simply stored for later processing. A future
 * AI-backed implementation (e.g. AssemblyAI / Whisper) can replace this bean
 * without any change to controllers, services, or the database schema.</p>
 */
public interface ReflectionTranscriptionService {

    /**
     * Attempt to transcribe a stored audio file.
     *
     * @param reflectionId the reflection the audio belongs to
     * @param subDir       storage bucket the file lives in
     * @param storedName   unique on-disk name of the audio file
     * @return the transcript, or {@code null} when transcription is unavailable
     */
    String transcribe(UUID reflectionId, String subDir, String storedName);

    /** Whether an automated transcription backend is currently wired in. */
    boolean isAvailable();
}
