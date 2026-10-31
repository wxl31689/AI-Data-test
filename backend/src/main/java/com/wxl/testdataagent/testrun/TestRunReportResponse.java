package com.wxl.testdataagent.testrun;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TestRunReportResponse(
        TestRunResponse run,
        double passRate,
        long totalDurationMs,
        double averageDurationMs,
        long p95DurationMs,
        long maxDurationMs,
        Map<ResultStatus, Long> resultDistribution,
        List<TestResultResponse> failures,
        Instant generatedAt
) {
}
