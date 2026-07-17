package com.kbv.education.entity;

import com.kbv.education.entity.enums.BackupStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One manual, admin-triggered database dump. No scheduling — Step 6 of Phase 5. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "backup_history")
public class BackupHistory extends BaseEntity {

    @Column(name = "file_path", columnDefinition = "text")
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackupStatus status = BackupStatus.IN_PROGRESS;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
}
