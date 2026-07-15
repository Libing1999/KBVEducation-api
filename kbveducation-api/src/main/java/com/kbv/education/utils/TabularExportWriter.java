package com.kbv.education.utils;

import com.kbv.education.dto.export.ExportFormat;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Renders a header row + data rows as CSV or XLSX bytes. No streaming - admin exports are small (cohort-scale). */
public final class TabularExportWriter {

    private TabularExportWriter() {
    }

    public static byte[] write(ExportFormat format, List<String> headers, List<List<Object>> rows) {
        return format == ExportFormat.XLSX ? writeXlsx(headers, rows) : writeCsv(headers, rows);
    }

    public static String contentType(ExportFormat format) {
        return format == ExportFormat.XLSX
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv";
    }

    public static String extension(ExportFormat format) {
        return format == ExportFormat.XLSX ? "xlsx" : "csv";
    }

    private static byte[] writeCsv(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers.stream().map(TabularExportWriter::csvField).toList())).append("\r\n");
        for (List<Object> row : rows) {
            sb.append(String.join(",", row.stream().map(v -> csvField(cellText(v))).toList())).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }

    private static byte[] writeXlsx(List<String> headers, List<List<Object>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Export");

            CellStyle headerStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);

            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < headers.size(); col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers.get(col));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (List<Object> row : rows) {
                Row dataRow = sheet.createRow(rowIndex++);
                for (int col = 0; col < row.size(); col++) {
                    setCellValue(dataRow.createCell(col), row.get(col));
                }
            }

            for (int col = 0; col < headers.size(); col++) {
                sheet.autoSizeColumn(col);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build XLSX export", e);
        }
    }

    private static void setCellValue(Cell cell, Object value) {
        switch (value) {
            case null -> cell.setBlank();
            case Number number -> cell.setCellValue(number.doubleValue());
            case Boolean bool -> cell.setCellValue(bool);
            default -> cell.setCellValue(value.toString());
        }
    }

    private static String cellText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal bd) {
            return bd.toPlainString();
        }
        return value.toString();
    }
}
