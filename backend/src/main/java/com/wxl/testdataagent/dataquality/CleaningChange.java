package com.wxl.testdataagent.dataquality;

public record CleaningChange(
        int rowNumber,
        String columnName,
        String originalValue,
        String cleanedValue,
        String reason
) {
}
