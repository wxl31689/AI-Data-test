package com.wxl.testdataagent.testcase;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Map;

public record TestRequestConfig(
        Map<String, Object> pathParams,
        Map<String, Object> queryParams,
        Map<String, String> headers,
        JsonNode body,
        @Min(value = 100, message = "超时时间不能小于 100 毫秒")
        @Max(value = 30000, message = "超时时间不能超过 30000 毫秒")
        Integer timeoutMillis
) {
    public TestRequestConfig {
        pathParams = pathParams == null ? Map.of() : Map.copyOf(pathParams);
        queryParams = queryParams == null ? Map.of() : Map.copyOf(queryParams);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        timeoutMillis = timeoutMillis == null ? 5000 : timeoutMillis;
    }
}

