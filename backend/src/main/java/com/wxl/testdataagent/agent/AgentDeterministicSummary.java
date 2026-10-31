package com.wxl.testdataagent.agent;

import com.wxl.testdataagent.testrun.RunStatus;

public record AgentDeterministicSummary(
        RunStatus runStatus,
        int totalCount,
        int processedCount,
        int passCount,
        int failCount,
        int errorCount,
        int skippedCount
) {
}
