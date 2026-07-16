package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row per export run (every dataset, old Phase 4 endpoints and new Step 3 ones alike). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "export_history")
public class ExportHistory extends BaseEntity {

    @Column(name = "dataset", nullable = false, length = 30)
    private String dataset;

    @Column(name = "format", nullable = false, length = 10)
    private String format;

    @Column(name = "filters_snapshot", columnDefinition = "text")
    private String filtersSnapshot;

    @Column(name = "row_count")
    private Integer rowCount;
}
