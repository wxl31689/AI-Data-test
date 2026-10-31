package com.wxl.testdataagent.dataquality;

import com.wxl.testdataagent.common.InvalidRequestException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataExportService {

    byte[] export(
            ParsedDataset dataset,
            DataExportFormat format,
            List<ColumnQualityRule> qualityRules
    ) {
        try {
            return format == DataExportFormat.CSV
                    ? exportCsv(dataset)
                    : exportXlsx(dataset, qualityRules);
        } catch (IOException exception) {
            throw new InvalidRequestException("数据导出失败", exception);
        }
    }

    private byte[] exportCsv(ParsedDataset dataset) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0xEF);
        output.write(0xBB);
        output.write(0xBF);
        try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(
                     writer,
                     CSVFormat.DEFAULT.builder()
                             .setHeader(dataset.headers().stream().map(this::csvSafe).toArray(String[]::new))
                             .build()
             )) {
            for (List<String> row : dataset.rows()) {
                printer.printRecord(row.stream().map(this::csvSafe).toList());
            }
        }
        return output.toByteArray();
    }

    private byte[] exportXlsx(
            ParsedDataset dataset,
            List<ColumnQualityRule> qualityRules
    ) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Data");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            Map<String, ExpectedDataType> expectedTypes = expectedTypes(qualityRules);

            Row header = sheet.createRow(0);
            for (int column = 0; column < dataset.headers().size(); column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(dataset.headers().get(column));
                cell.setCellStyle(headerStyle);
            }

            for (int rowIndex = 0; rowIndex < dataset.rows().size(); rowIndex++) {
                Row targetRow = sheet.createRow(rowIndex + 1);
                List<String> sourceRow = dataset.rows().get(rowIndex);
                for (int column = 0; column < dataset.headers().size(); column++) {
                    Cell cell = targetRow.createCell(column);
                    setTypedValue(
                            cell,
                            sourceRow.get(column),
                            expectedTypes.get(dataset.headers().get(column)),
                            dateStyle
                    );
                }
            }

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(
                    0,
                    Math.max(0, dataset.rows().size()),
                    0,
                    dataset.headers().size() - 1
            ));
            applyColumnWidths(sheet, dataset);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private Map<String, ExpectedDataType> expectedTypes(List<ColumnQualityRule> rules) {
        Map<String, ExpectedDataType> result = new HashMap<>();
        if (rules != null) {
            for (ColumnQualityRule rule : rules) result.put(rule.columnName(), rule.expectedType());
        }
        return result;
    }

    private void setTypedValue(
            Cell cell,
            String value,
            ExpectedDataType type,
            CellStyle dateStyle
    ) {
        if (value == null || value.isBlank() || type == null || type == ExpectedDataType.STRING) {
            cell.setCellValue(value == null ? "" : value);
            return;
        }
        try {
            switch (type) {
                case INTEGER, DECIMAL -> {
                    BigDecimal number = new BigDecimal(value.trim());
                    if (number.precision() > 15) {
                        cell.setCellValue(value);
                    } else {
                        cell.setCellValue(number.doubleValue());
                    }
                }
                case BOOLEAN -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        cell.setCellValue(value);
                    } else {
                        cell.setCellValue(Boolean.parseBoolean(value));
                    }
                }
                case DATE -> {
                    cell.setCellValue(LocalDate.parse(value.trim()));
                    cell.setCellStyle(dateStyle);
                }
                case STRING -> cell.setCellValue(value);
            }
        } catch (NumberFormatException | DateTimeParseException exception) {
            cell.setCellValue(value);
        }
    }

    private void applyColumnWidths(Sheet sheet, ParsedDataset dataset) {
        for (int column = 0; column < dataset.headers().size(); column++) {
            int maxChars = dataset.headers().get(column).length();
            int rowLimit = Math.min(dataset.rows().size(), 200);
            for (int row = 0; row < rowLimit; row++) {
                maxChars = Math.max(maxChars, dataset.rows().get(row).get(column).length());
            }
            sheet.setColumnWidth(column, Math.min(Math.max(maxChars + 2, 10), 40) * 256);
        }
    }

    private String csvSafe(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        char first = value.charAt(0);
        boolean negativeNumber = first == '-' && isNumeric(value);
        if (first == '=' || first == '+' || first == '@' || first == '\t' || first == '\r'
                || (first == '-' && !negativeNumber)) {
            return "'" + value;
        }
        return value;
    }

    private boolean isNumeric(String value) {
        try {
            new BigDecimal(value.trim());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
