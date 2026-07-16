package com.kbv.education.service.export;

import com.kbv.education.dto.export.ExportFormat;
import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.entity.ExportHistory;
import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.repository.ExportHistoryRepository;
import com.kbv.education.utils.TabularExportWriter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One generic orchestrator for every {@link ExportDataset} — the per-dataset
 * knowledge lives entirely in each {@link ExportDatasetHandler} bean. Keeps
 * this from becoming the same one-method-per-dataset sprawl the module would
 * have if it followed the Phase 4 {@code ExportServiceImpl} style directly
 * (that file is left untouched; this sits alongside it for the new datasets).
 */
@Service
public class GenericExportService {

    private final Map<ExportDataset, ExportDatasetHandler> handlers;
    private final ExportHistoryRepository exportHistoryRepository;

    public GenericExportService(List<ExportDatasetHandler> handlerBeans,
                                 ExportHistoryRepository exportHistoryRepository) {
        this.handlers = handlerBeans.stream()
                .collect(Collectors.toMap(ExportDatasetHandler::dataset, Function.identity()));
        this.exportHistoryRepository = exportHistoryRepository;
    }

    public List<DatasetMetadata> listDatasets() {
        return handlers.values().stream()
                .map(h -> new DatasetMetadata(h.dataset(), h.label(), h.supportedFilters()))
                .sorted((a, b) -> a.label().compareTo(b.label()))
                .toList();
    }

    @Transactional
    public FileDownloadResult export(ExportDataset dataset, ExportFormat format, ExportFilters filters) {
        ExportDatasetHandler handler = handlers.get(dataset);
        if (handler == null) {
            throw new BusinessRuleException("No export handler registered for dataset " + dataset);
        }

        List<String> headers = handler.headers();
        List<List<Object>> rows = handler.rows(filters);
        byte[] bytes = TabularExportWriter.write(format, headers, rows);

        recordHistory(dataset.name(), format.name(), filters, rows.size());

        String fileName = handler.fileNamePrefix() + "." + TabularExportWriter.extension(format);
        return new FileDownloadResult(fileName, TabularExportWriter.contentType(format), bytes.length,
                new ByteArrayResource(bytes));
    }

    /** Additive history logging, reused by the Phase 4 endpoints too (see ExportServiceImpl). */
    public void recordHistory(String dataset, String format, ExportFilters filters, int rowCount) {
        ExportHistory history = new ExportHistory();
        history.setDataset(dataset);
        history.setFormat(format);
        history.setFiltersSnapshot(filters == null ? null : filters.toString());
        history.setRowCount(rowCount);
        exportHistoryRepository.save(history);
    }

    public record DatasetMetadata(ExportDataset dataset, String label, Set<ExportFilterType> supportedFilters) {
    }
}
