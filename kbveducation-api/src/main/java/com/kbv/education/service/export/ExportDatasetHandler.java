package com.kbv.education.service.export;

import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;

import java.util.List;
import java.util.Set;

/** One implementation per {@link ExportDataset} — the only per-dataset code the registry needs. */
public interface ExportDatasetHandler {

    ExportDataset dataset();

    /** Human-readable label for the frontend's dataset picker. */
    String label();

    Set<ExportFilterType> supportedFilters();

    List<String> headers();

    List<List<Object>> rows(ExportFilters filters);

    /** Used to build the download file name, e.g. "students". */
    String fileNamePrefix();
}
