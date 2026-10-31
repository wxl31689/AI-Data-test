package com.wxl.testdataagent.dataquality;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CleanDataRequest(
        @Size(max = 200, message = "清洗列规则不能超过 200 条")
        List<@Valid ColumnCleaningRule> columns,

        boolean removeDuplicates,

        @Size(max = 200, message = "重复键列不能超过 200 个")
        List<String> duplicateKeyColumns
) {
    public CleanDataRequest {
        columns = columns == null ? List.of() : List.copyOf(columns);
        duplicateKeyColumns = duplicateKeyColumns == null ? List.of() : List.copyOf(duplicateKeyColumns);
    }

    public static CleanDataRequest empty() {
        return new CleanDataRequest(List.of(), false, List.of());
    }
}
