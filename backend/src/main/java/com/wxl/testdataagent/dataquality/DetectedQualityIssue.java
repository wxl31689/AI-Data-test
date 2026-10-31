package com.wxl.testdataagent.dataquality;

public record DetectedQualityIssue(
        int rowNumber,
        String columnName,
        DataRuleCode ruleCode,
        String originalValue,
        IssueSeverity severity,
        String description
) {
}
