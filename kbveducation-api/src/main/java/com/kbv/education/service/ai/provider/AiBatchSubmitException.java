package com.kbv.education.service.ai.provider;

import com.kbv.education.entity.enums.AiFailureReason;
import lombok.Getter;

/** A batch could not be created; every job in it counts as failed for this reason. */
@Getter
public class AiBatchSubmitException extends Exception {

    private final AiFailureReason reason;

    public AiBatchSubmitException(AiFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }
}
