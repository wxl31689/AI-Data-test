package com.wxl.testdataagent.dataquality;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ColumnCleaningRule(
        @NotBlank(message = "清洗列名不能为空")
        String columnName,

        boolean trimWhitespace,

        @Size(max = 500, message = "空值替换内容不能超过 500 个字符")
        String emptyReplacement
) {
}
