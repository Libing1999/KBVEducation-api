package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** An optional attachment on a {@link PracticeSession}. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "practice_files")
public class PracticeFile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practice_session_id", nullable = false)
    private PracticeSession practiceSession;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "stored_name", nullable = false, length = 255, unique = true)
    private String storedName;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_date", nullable = false)
    private Instant uploadedDate = Instant.now();
}
