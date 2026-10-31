package com.wxl.testdataagent.dataquality;

import com.wxl.testdataagent.common.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DataCleaningService {

    private static final int MAX_CHANGE_DETAILS = 200;
    private static final int MAX_REMOVED_ROW_DETAILS = 200;

    CleaningResult clean(ParsedDataset source, CleanDataRequest request) {
        Map<String, Integer> indexes = buildIndexes(source.headers());
        Map<String, ColumnCleaningRule> rules = validateRules(request, indexes);
        List<String> keyColumns = request.duplicateKeyColumns().isEmpty()
                ? source.headers()
                : request.duplicateKeyColumns();

        List<List<String>> outputRows = new ArrayList<>();
        List<CleaningChange> changes = new ArrayList<>();
        List<Integer> removedRows = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        long changedCellCount = 0;
        int removedDuplicateCount = 0;

        for (int rowIndex = 0; rowIndex < source.rows().size(); rowIndex++) {
            int sourceRowNumber = rowIndex + 2;
            List<String> originalRow = source.rows().get(rowIndex);
            List<String> cleanedRow = new ArrayList<>(originalRow);
            List<CleaningChange> rowChanges = new ArrayList<>();

            for (ColumnCleaningRule rule : rules.values()) {
                int columnIndex = indexes.get(rule.columnName());
                String original = originalRow.get(columnIndex);
                String cleaned = original;
                List<String> reasons = new ArrayList<>();
                if (rule.trimWhitespace()) {
                    String trimmed = cleaned.strip();
                    if (!cleaned.equals(trimmed)) {
                        cleaned = trimmed;
                        reasons.add("去除首尾空白");
                    }
                }
                if (cleaned.isBlank() && rule.emptyReplacement() != null
                        && !cleaned.equals(rule.emptyReplacement())) {
                    cleaned = rule.emptyReplacement();
                    reasons.add("填充空值");
                }
                if (!original.equals(cleaned)) {
                    cleanedRow.set(columnIndex, cleaned);
                    rowChanges.add(new CleaningChange(
                            sourceRowNumber,
                            rule.columnName(),
                            original,
                            cleaned,
                            String.join("、", reasons)
                    ));
                }
            }

            if (request.removeDuplicates()) {
                String key = buildKey(cleanedRow, keyColumns, indexes);
                if (!seenKeys.add(key)) {
                    removedDuplicateCount++;
                    if (removedRows.size() < MAX_REMOVED_ROW_DETAILS) removedRows.add(sourceRowNumber);
                    continue;
                }
            }

            outputRows.add(List.copyOf(cleanedRow));
            changedCellCount += rowChanges.size();
            int remaining = MAX_CHANGE_DETAILS - changes.size();
            if (remaining > 0) changes.addAll(rowChanges.stream().limit(remaining).toList());
        }

        ParsedDataset cleaned = new ParsedDataset(
                source.headers(),
                List.copyOf(outputRows),
                source.sheetName()
        );
        return new CleaningResult(
                cleaned,
                changedCellCount,
                removedDuplicateCount,
                List.copyOf(removedRows),
                List.copyOf(changes)
        );
    }

    private Map<String, ColumnCleaningRule> validateRules(
            CleanDataRequest request,
            Map<String, Integer> indexes
    ) {
        Map<String, ColumnCleaningRule> rules = new LinkedHashMap<>();
        for (ColumnCleaningRule rule : request.columns()) {
            if (!indexes.containsKey(rule.columnName())) {
                throw new InvalidRequestException("清洗规则引用了不存在的列: " + rule.columnName());
            }
            if (rules.putIfAbsent(rule.columnName(), rule) != null) {
                throw new InvalidRequestException("同一列不能重复配置清洗规则: " + rule.columnName());
            }
        }
        Set<String> duplicateColumns = new HashSet<>();
        for (String column : request.duplicateKeyColumns()) {
            if (!indexes.containsKey(column)) {
                throw new InvalidRequestException("重复键引用了不存在的列: " + column);
            }
            if (!duplicateColumns.add(column)) {
                throw new InvalidRequestException("重复键列不能重复: " + column);
            }
        }
        return rules;
    }

    private Map<String, Integer> buildIndexes(List<String> headers) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) indexes.put(headers.get(index), index);
        return indexes;
    }

    private String buildKey(
            List<String> row,
            List<String> keyColumns,
            Map<String, Integer> indexes
    ) {
        StringBuilder key = new StringBuilder();
        for (String column : keyColumns) {
            String value = row.get(indexes.get(column));
            key.append(value.length()).append(':').append(value).append('|');
        }
        return key.toString();
    }
}
