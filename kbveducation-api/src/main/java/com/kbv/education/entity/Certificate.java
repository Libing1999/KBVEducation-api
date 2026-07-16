package com.kbv.education.entity;

import com.kbv.education.entity.enums.CertificateStatus;
import com.kbv.education.entity.enums.CertificateType;
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

/**
 * A single issued certificate. The PDF is rendered once at generation time
 * and stored under {@link #filePath} — downloading never re-renders it, so
 * editing or deactivating {@link #template} afterwards can't retroactively
 * change a certificate a student already received. Branding is snapshotted
 * onto this row for the same reason.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "certificates")
public class Certificate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private CertificateTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type", nullable = false, length = 20)
    private CertificateType certificateType;

    @Column(name = "certificate_number", nullable = false, length = 40)
    private String certificateNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort;

    @Column(name = "tier_at_issue", length = 30)
    private String tierAtIssue;

    @Column(name = "file_path", nullable = false, columnDefinition = "text")
    private String filePath;

    @Column(name = "institution_name_snapshot", nullable = false, length = 150)
    private String institutionNameSnapshot;

    @Column(name = "logo_path_snapshot", columnDefinition = "text")
    private String logoPathSnapshot;

    @Column(name = "primary_color_snapshot", nullable = false, length = 7)
    private String primaryColorSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CertificateStatus status = CertificateStatus.ISSUED;
}
