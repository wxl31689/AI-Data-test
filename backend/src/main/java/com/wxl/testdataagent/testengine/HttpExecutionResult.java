package com.wxl.testdataagent.testengine;

public record HttpExecutionResult(
        String requestSummary,
        String responseSummary,
        String responseBody,
        int statusCode,
        long durationMs
) {
}

