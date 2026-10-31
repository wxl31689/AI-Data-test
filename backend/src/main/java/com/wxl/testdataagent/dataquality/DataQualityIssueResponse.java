package com.wxl.testdataagent.dataquality;

import java.time.Instant;

public record DataQualityIssueResponse(
        String id,
        int rowNumber,
        String columnName,
        DataRuleCode ruleCode,
        String originalValue,
        IssueSeverity severity,
        String description,
        Instant createdAt
) {
    static DataQualityIssueResponse from(DataQualityIssueEntity entity) {
        return new DataQualityIssueResponse(
                entity.getId(),
                entity.getRowNumber(),
                entity.getColumnName(),
                entity.getRuleCode(),
                entity.getOriginalValue(),
                entity.getSeverity(),
                entity.getDescription(),
                entity.getCreatedAt()
        );
    }
}
