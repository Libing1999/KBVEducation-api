package com.kbv.education.service.export;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The 4 generic filters an export request may supply. Each {@link ExportDatasetHandler}
 * only honors the subset it declares via {@code supportedFilters()} — an
 * unsupported filter value is simply ignored, not an error, since query
 * params are shared across every dataset in one endpoint.
 */
public record ExportFilters(LocalDate from, LocalDate to, UUID cohortId, UUID studentId, String status) {
}
