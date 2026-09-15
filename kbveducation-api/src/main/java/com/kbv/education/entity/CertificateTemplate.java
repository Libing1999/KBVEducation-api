package com.kbv.education.entity;

import com.kbv.education.entity.enums.CertificateType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An admin-editable certificate layout: a body-text template with
 * {@code {{placeholder}}} tokens plus branding fields. At most one template
 * per {@link CertificateType} may be {@code active} at a time — that's the
 * template {@link CertificateService} resolves when an admin generates a
 * certificate of that type.
 *
 * <p><b>Since the 27.08.2026 KBV design refresh</b>, {@link CertificateType#TIER_1},
 * {@link CertificateType#TIER_2} and {@link CertificateType#TIER_3} certificates render
 * from a fixed, customer-approved layout (see {@code CertificatePdfRenderer}) rather than
 * from {@link #bodyTemplate} / {@link #primaryColorHex} / {@link #institutionNameOverride} /
 * {@link #logoPathOverride} — those fields are still required by this row's CRUD API (an
 * active template row must still exist per type for {@code generate()} to resolve) but are
 * no longer rendered into the certificate for those three types. They still fully apply to
 * {@link CertificateType#COMPLETION}, which the design refresh did not cover and which still
 * renders through the original generic bordered-frame template.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "certificate_templates")
public class CertificateTemplate extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type", nullable = false, length = 20)
    private CertificateType certificateType;

    /** Unused for TIER_1/TIER_2/TIER_3 (fixed design) — see class Javadoc. Still used by COMPLETION. */
    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    /** Unused for TIER_1/TIER_2/TIER_3 (fixed design) — see class Javadoc. Still used by COMPLETION. */
    @Column(name = "primary_color_hex", nullable = false, length = 7)
    private String primaryColorHex = "#1B3A6B";

    /** Unused for TIER_1/TIER_2/TIER_3 (fixed design) — see class Javadoc. Still used by COMPLETION. */
    @Column(name = "institution_name_override", length = 150)
    private String institutionNameOverride;

    /** Unused for TIER_1/TIER_2/TIER_3 (fixed design) — see class Javadoc. Still used by COMPLETION. */
    @Column(name = "logo_path_override", columnDefinition = "text")
    private String logoPathOverride;

    @Column(name = "active", nullable = false)
    private boolean active = false;
}
