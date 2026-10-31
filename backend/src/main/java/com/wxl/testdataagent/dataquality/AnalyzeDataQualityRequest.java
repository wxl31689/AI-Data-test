package com.wxl.testdataagent.dataquality;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AnalyzeDataQualityRequest(
        @NotEmpty(message = "至少配置一列质量规则")
        List<@Valid ColumnQualityRule> columns,
        List<String> duplicateKeyColumns
) {
    public AnalyzeDataQualityRequest {
        duplicateKeyColumns = duplicateKeyColumns == null ? List.of() : List.copyOf(duplicateKeyColumns);
    }
}
