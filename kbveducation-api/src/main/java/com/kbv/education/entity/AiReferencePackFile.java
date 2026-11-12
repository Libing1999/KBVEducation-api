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

/** One Markdown file inside a reference pack version. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "ai_reference_pack_files")
public class AiReferencePackFile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_version_id", nullable = false)
    private AiReferencePackVersion packVersion;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;
}
