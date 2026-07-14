package com.kbv.education.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Default, no-AI implementation. Reflection audio is kept as-is for future
 * processing; nothing is transcribed now. Replace this bean (e.g. with a
 * {@code @Primary} AI implementation) to enable automatic transcription later.
 */
@Slf4j
@Service
public class ManualReflectionTranscriptionService implements ReflectionTranscriptionService {

    @Override
    public String transcribe(UUID reflectionId, String subDir, String storedName) {
        log.debug("Manual transcription: audio {} for reflection {} stored, no transcript produced",
                storedName, reflectionId);
        return null;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
