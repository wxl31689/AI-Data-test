package com.wxl.testdataagent.testrun;

import java.time.Instant;
import java.util.List;

public record TestResultResponse(
        String id,
        String testCaseId,
        String caseName,
        String requestSummary,
        String responseSummary,
        Integer statusCode,
        Long durationMs,
        ResultStatus resultStatus,
        String errorMessage,
        Instant createdAt,
        List<AssertionResultResponse> assertions
) {
}

