package com.kbv.education.dto.export;

import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;

import java.util.Set;

public record ExportDatasetMetadataResponse(ExportDataset dataset, String label, Set<ExportFilterType> supportedFilters) {
}
