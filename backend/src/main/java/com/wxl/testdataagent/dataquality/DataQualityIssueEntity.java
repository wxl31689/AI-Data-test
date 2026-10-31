package com.wxl.testdataagent.dataquality;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_quality_issue")
public class DataQualityIssueEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "task_id", length = 36, nullable = false)
    private String taskId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "column_name", length = 160, nullable = false)
    private String columnName;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", length = 32, nullable = false)
    private DataRuleCode ruleCode;

    @Column(name = "original_value", columnDefinition = "TEXT")
    private String originalValue;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private IssueSeverity severity;

    @Column(length = 1000, nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DataQualityIssueEntity() {
    }

    public DataQualityIssueEntity(
            String taskId,
            int rowNumber,
            String columnName,
            DataRuleCode ruleCode,
            String originalValue,
            IssueSeverity severity,
            String description
    ) {
        this.id = UUID.randomUUID().toString();
        this.taskId = taskId;
        this.rowNumber = rowNumber;
        this.columnName = columnName;
        this.ruleCode = ruleCode;
        this.originalValue = originalValue;
        this.severity = severity;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTaskId() { return taskId; }
    public int getRowNumber() { return rowNumber; }
    public String getColumnName() { return columnName; }
    public DataRuleCode getRuleCode() { return ruleCode; }
    public String getOriginalValue() { return originalValue; }
    public IssueSeverity getSeverity() { return severity; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
