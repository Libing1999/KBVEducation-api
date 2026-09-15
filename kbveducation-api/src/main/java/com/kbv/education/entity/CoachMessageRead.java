package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Per-recipient read marker for a {@link CoachMessage}. {@code reader} is
 * whichever account actually viewed it — the addressed student, or a parent
 * viewing their linked student's copy of the same message — so one reader's
 * read state never leaks to another reader of the same (possibly collective)
 * message.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "coach_message_read",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cmr_message_reader",
                columnNames = {"message_id", "reader_id"}
        )
)
public class CoachMessageRead extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private CoachMessage message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reader_id", nullable = false)
    private User reader;

    @Column(name = "read_at", nullable = false)
    private Instant readAt = Instant.now();
}
