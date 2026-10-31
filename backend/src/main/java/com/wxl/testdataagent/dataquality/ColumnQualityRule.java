package com.wxl.testdataagent.dataquality;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ColumnQualityRule(
        @NotBlank(message = "列名不能为空")
        String columnName,

        @NotNull(message = "预期类型不能为空")
        ExpectedDataType expectedType,

        boolean required,
        BigDecimal min,
        BigDecimal max
) {
}
