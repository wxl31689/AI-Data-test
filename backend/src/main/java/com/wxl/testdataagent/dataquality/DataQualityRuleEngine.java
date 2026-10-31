package com.wxl.testdataagent.dataquality;

import com.wxl.testdataagent.common.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DataQualityRuleEngine {

    public List<DetectedQualityIssue> analyze(
            ParsedDataset dataset,
            List<ColumnQualityRule> rules,
            List<String> duplicateKeyColumns
    ) {
        Map<String, Integer> indexes = buildIndexes(dataset.headers());
        validateRules(rules, duplicateKeyColumns, indexes);

        List<DetectedQualityIssue> issues = new ArrayList<>();
        Set<String> issueKeys = new LinkedHashSet<>();
        for (int rowIndex = 0; rowIndex < dataset.rows().size(); rowIndex++) {
            List<String> row = dataset.rows().get(rowIndex);
            int sourceRowNumber = rowIndex + 2;
            for (ColumnQualityRule rule : rules) {
                String value = row.get(indexes.get(rule.columnName()));
                if (isBlank(value)) {
                    if (rule.required()) {
                        addIssue(issues, issueKeys, new DetectedQualityIssue(
                                sourceRowNumber,
                                rule.columnName(),
                                DataRuleCode.REQUIRED,
                                value,
                                IssueSeverity.ERROR,
                                "必填字段不能为空"
                        ));
                    }
                    continue;
                }

                BigDecimal numericValue = parseNumeric(value, rule.expectedType());
                if (!matchesType(value, rule.expectedType())) {
                    addIssue(issues, issueKeys, new DetectedQualityIssue(
                            sourceRowNumber,
                            rule.columnName(),
                            DataRuleCode.TYPE_MISMATCH,
                            value,
                            IssueSeverity.ERROR,
                            "字段无法转换为预期类型 " + rule.expectedType().name()
                    ));
                    continue;
                }

                if (numericValue != null && outsideRange(numericValue, rule.min(), rule.max())) {
                    addIssue(issues, issueKeys, new DetectedQualityIssue(
                            sourceRowNumber,
                            rule.columnName(),
                            DataRuleCode.NUM_RANGE,
                            value,
                            IssueSeverity.ERROR,
                            rangeDescription(rule.min(), rule.max())
                    ));
                }
            }
        }

        detectDuplicates(dataset, duplicateKeyColumns, indexes, issues, issueKeys);
        return List.copyOf(issues);
    }

    private void detectDuplicates(
            ParsedDataset dataset,
            List<String> requestedKeyColumns,
            Map<String, Integer> indexes,
            List<DetectedQualityIssue> issues,
            Set<String> issueKeys
    ) {
        List<String> keyColumns = requestedKeyColumns == null || requestedKeyColumns.isEmpty()
                ? dataset.headers()
                : requestedKeyColumns;
        Set<String> seen = new HashSet<>();
        for (int rowIndex = 0; rowIndex < dataset.rows().size(); rowIndex++) {
            List<String> row = dataset.rows().get(rowIndex);
            StringBuilder key = new StringBuilder();
            for (String column : keyColumns) {
                String value = row.get(indexes.get(column));
                key.append(value.length()).append(':').append(value).append('|');
            }
            if (!seen.add(key.toString())) {
                String columnLabel = requestedKeyColumns == null || requestedKeyColumns.isEmpty()
                        ? "<整行>"
                        : String.join(", ", keyColumns);
                String original = keyColumns.stream()
                        .map(column -> row.get(indexes.get(column)))
                        .reduce((left, right) -> left + " | " + right)
                        .orElse("");
                addIssue(issues, issueKeys, new DetectedQualityIssue(
                        rowIndex + 2,
                        columnLabel,
                        DataRuleCode.DUPLICATE_KEY,
                        original,
                        IssueSeverity.WARNING,
                        "与前面的记录重复"
                ));
            }
        }
    }

    private Map<String, Integer> buildIndexes(List<String> headers) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) indexes.put(headers.get(i), i);
        return indexes;
    }

    private void validateRules(
            List<ColumnQualityRule> rules,
            List<String> duplicateKeyColumns,
            Map<String, Integer> indexes
    ) {
        Set<String> configured = new HashSet<>();
        for (ColumnQualityRule rule : rules) {
            if (!indexes.containsKey(rule.columnName())) {
                throw new InvalidRequestException("质量规则引用了不存在的列: " + rule.columnName());
            }
            if (!configured.add(rule.columnName())) {
                throw new InvalidRequestException("同一列不能重复配置规则: " + rule.columnName());
            }
            boolean numeric = rule.expectedType() == ExpectedDataType.INTEGER
                    || rule.expectedType() == ExpectedDataType.DECIMAL;
            if (!numeric && (rule.min() != null || rule.max() != null)) {
                throw new InvalidRequestException("只有整数或小数列可配置数值范围: " + rule.columnName());
            }
            if (rule.min() != null && rule.max() != null && rule.min().compareTo(rule.max()) > 0) {
                throw new InvalidRequestException("列 " + rule.columnName() + " 的最小值不能大于最大值");
            }
        }
        if (duplicateKeyColumns != null) {
            for (String column : duplicateKeyColumns) {
                if (!indexes.containsKey(column)) {
                    throw new InvalidRequestException("重复键引用了不存在的列: " + column);
                }
            }
        }
    }

    private boolean matchesType(String value, ExpectedDataType expectedType) {
        try {
            return switch (expectedType) {
                case STRING -> true;
                case INTEGER -> new BigDecimal(value.trim()).stripTrailingZeros().scale() <= 0;
                case DECIMAL -> {
                    new BigDecimal(value.trim());
                    yield true;
                }
                case BOOLEAN -> value.trim().equalsIgnoreCase("true")
                        || value.trim().equalsIgnoreCase("false");
                case DATE -> {
                    LocalDate.parse(value.trim());
                    yield true;
                }
            };
        } catch (NumberFormatException | DateTimeParseException exception) {
            return false;
        }
    }

    private BigDecimal parseNumeric(String value, ExpectedDataType expectedType) {
        if (expectedType != ExpectedDataType.INTEGER && expectedType != ExpectedDataType.DECIMAL) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean outsideRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        return (min != null && value.compareTo(min) < 0)
                || (max != null && value.compareTo(max) > 0);
    }

    private String rangeDescription(BigDecimal min, BigDecimal max) {
        String lower = min == null ? "-∞" : min.stripTrailingZeros().toPlainString();
        String upper = max == null ? "+∞" : max.stripTrailingZeros().toPlainString();
        return "数值不在允许范围 [" + lower + ", " + upper + "] 内";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void addIssue(
            List<DetectedQualityIssue> issues,
            Set<String> issueKeys,
            DetectedQualityIssue issue
    ) {
        String key = issue.rowNumber() + "|" + issue.columnName() + "|" + issue.ruleCode();
        if (issueKeys.add(key)) issues.add(issue);
    }
}
