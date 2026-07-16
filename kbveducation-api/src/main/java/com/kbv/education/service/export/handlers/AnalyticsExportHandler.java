package com.kbv.education.service.export.handlers;

import com.kbv.education.entity.DashboardMetric;
import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;
import com.kbv.education.repository.DashboardMetricRepository;
import com.kbv.education.service.export.ExportDatasetHandler;
import com.kbv.education.service.export.ExportFilters;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Cached {@code dashboard_metrics} snapshot rows, not a dated activity list —
 * only COHORT applies (null = platform-wide metrics); DATE/STUDENT/STATUS
 * don't map onto this dataset's shape.
 */
@Component
@RequiredArgsConstructor
public class AnalyticsExportHandler implements ExportDatasetHandler {

    private final DashboardMetricRepository dashboardMetricRepository;

    @Override
    public ExportDataset dataset() {
        return ExportDataset.ANALYTICS;
    }

    @Override
    public String label() {
        return "Analytics";
    }

    @Override
    public Set<ExportFilterType> supportedFilters() {
        return EnumSet.of(ExportFilterType.COHORT);
    }

    @Override
    public List<String> headers() {
        return List.of("Metric", "Value", "Computed At");
    }

    @Override
    public List<List<Object>> rows(ExportFilters filters) {
        List<DashboardMetric> metrics = filters.cohortId() == null
                ? dashboardMetricRepository.findByCohortIsNullAndDeletedFalse()
                : dashboardMetricRepository.findByCohort_IdAndDeletedFalse(filters.cohortId());

        return metrics.stream().map(this::toRow).toList();
    }

    private List<Object> toRow(DashboardMetric metric) {
        return List.of(metric.getMetricKey().name(), metric.getMetricValue(), metric.getComputedAt().toString());
    }

    @Override
    public String fileNamePrefix() {
        return "analytics";
    }
}
