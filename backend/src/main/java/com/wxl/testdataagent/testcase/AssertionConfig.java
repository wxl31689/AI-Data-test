package com.wxl.testdataagent.testcase;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AssertionConfig(
        @NotNull(message = "断言类型不能为空")
        AssertionType type,
        String path,
        JsonNode expected,
        JsonValueType expectedType,
        BigDecimal min,
        BigDecimal max,
        Long maxMillis
) {
    public AssertionConfig(AssertionType type, String path, JsonNode expected) {
        this(type, path, expected, null, null, null, null);
    }
}

