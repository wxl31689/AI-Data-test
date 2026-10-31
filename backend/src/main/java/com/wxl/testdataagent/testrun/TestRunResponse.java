package com.wxl.testdataagent.testrun;

import java.time.Instant;
import java.util.List;

public record TestRunResponse(
        String id,
        String projectId,
        String runName,
        TriggerType triggerType,
        RunStatus status,
        int totalCount,
        int processedCount,
        int passCount,
        int failCount,
        int errorCount,
        int skippedCount,
        Instant startedAt,
        Instant finishedAt,
        String parentRunId,
        List<TestResultResponse> results
) {
}

