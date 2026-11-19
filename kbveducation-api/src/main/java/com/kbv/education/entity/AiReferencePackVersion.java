package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One saved version of the reference pack (the admin's Markdown rules for the AI).
 * Versions are never changed after creation; every edit creates a new version,
 * and exactly one version is active at a time.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "ai_reference_pack_versions")
public class AiReferencePackVersion extends BaseEntity {

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "active", nullable = false)
    private boolean active = false;
}
