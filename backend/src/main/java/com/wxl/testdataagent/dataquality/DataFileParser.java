package com.wxl.testdataagent.dataquality;

import com.wxl.testdataagent.common.InvalidRequestException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DataFileParser {

    private final int maxRows;
    private final int maxColumns;

    public DataFileParser(
            @Value("${data-quality.max-rows}") int maxRows,
            @Value("${data-quality.max-columns}") int maxColumns
    ) {
        this.maxRows = maxRows;
        this.maxColumns = maxColumns;
    }

    public ParsedDataset parse(Path path, DataFileType fileType, String requestedSheetName) {
        try {
            return fileType == DataFileType.CSV
                    ? parseCsv(path)
                    : parseXlsx(path, requestedSheetName);
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidRequestException("文件无法解析，请确认格式与内容正确", exception);
        }
    }

    private ParsedDataset parseCsv(Path path) throws IOException {
        List<String> headers = null;
        List<List<String>> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.parse(reader)) {
            for (CSVRecord record : parser) {
                if (headers == null) {
                    headers = new ArrayList<>();
                    for (String value : record) headers.add(stripBom(value).trim());
                    validateHeaders(headers);
                    continue;
                }
                if (rows.size() >= maxRows) {
                    throw new InvalidRequestException("数据行数超过上限 " + maxRows);
                }
                if (record.size() > headers.size()) {
                    throw new InvalidRequestException("CSV 第 " + (record.getRecordNumber() + 1) + " 行的列数超过表头");
                }
                List<String> row = new ArrayList<>(headers.size());
                for (int i = 0; i < headers.size(); i++) {
                    row.add(i < record.size() ? record.get(i) : "");
                }
                rows.add(List.copyOf(row));
            }
        }

        if (headers == null) {
            throw new InvalidRequestException("CSV 文件为空");
        }
        if (rows.isEmpty()) {
            throw new InvalidRequestException("CSV 只包含表头，没有可分析的数据行");
        }
        return new ParsedDataset(List.copyOf(headers), List.copyOf(rows), null);
    }

    private ParsedDataset parseXlsx(Path path, String requestedSheetName) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(path))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new InvalidRequestException("XLSX 不包含工作表");
            }
            Sheet sheet = requestedSheetName == null || requestedSheetName.isBlank()
                    ? workbook.getSheetAt(0)
                    : workbook.getSheet(requestedSheetName);
            if (sheet == null) {
                throw new InvalidRequestException("指定的工作表不存在: " + requestedSheetName);
            }
            if (sheet.getNumMergedRegions() > 0) {
                throw new InvalidRequestException("首版不支持包含合并单元格的 XLSX");
            }

            int headerRowNumber = sheet.getFirstRowNum();
            Row headerRow = sheet.getRow(headerRowNumber);
            if (headerRow == null || headerRow.getLastCellNum() <= 0) {
                throw new InvalidRequestException("XLSX 没有可用的单行表头");
            }
            int columnCount = headerRow.getLastCellNum();
            if (columnCount > maxColumns) {
                throw new InvalidRequestException("列数超过上限 " + maxColumns);
            }
            List<String> headers = new ArrayList<>(columnCount);
            for (int column = 0; column < columnCount; column++) {
                headers.add(cellText(headerRow.getCell(column)).trim());
            }
            validateHeaders(headers);

            List<List<String>> rows = new ArrayList<>();
            for (int rowNumber = headerRowNumber + 1; rowNumber <= sheet.getLastRowNum(); rowNumber++) {
                if (rows.size() >= maxRows) {
                    throw new InvalidRequestException("数据行数超过上限 " + maxRows);
                }
                Row sourceRow = sheet.getRow(rowNumber);
                if (sourceRow != null && sourceRow.getLastCellNum() > columnCount) {
                    throw new InvalidRequestException("XLSX 第 " + (rowNumber + 1) + " 行的列数超过表头");
                }
                List<String> row = new ArrayList<>(columnCount);
                for (int column = 0; column < columnCount; column++) {
                    row.add(sourceRow == null ? "" : cellText(sourceRow.getCell(column)));
                }
                rows.add(List.copyOf(row));
            }
            if (rows.isEmpty()) {
                throw new InvalidRequestException("XLSX 只包含表头，没有可分析的数据行");
            }
            return new ParsedDataset(List.copyOf(headers), List.copyOf(rows), sheet.getSheetName());
        }
    }

    private String cellText(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.FORMULA) {
            throw new InvalidRequestException("首版不支持包含公式单元格的 XLSX");
        }
        if (cell.getCellType() == CellType.ERROR) {
            throw new InvalidRequestException("XLSX 包含错误单元格");
        }
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue();
        if (cell.getCellType() == CellType.BOOLEAN) return Boolean.toString(cell.getBooleanCellValue());
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return cell.toString();
    }

    private void validateHeaders(List<String> headers) {
        if (headers.isEmpty()) throw new InvalidRequestException("文件缺少表头");
        if (headers.size() > maxColumns) throw new InvalidRequestException("列数超过上限 " + maxColumns);
        Set<String> unique = new HashSet<>();
        for (String header : headers) {
            if (header.isBlank()) throw new InvalidRequestException("表头不能包含空列名");
            if (!unique.add(header)) throw new InvalidRequestException("表头包含重复列名: " + header);
        }
    }

    private String stripBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
