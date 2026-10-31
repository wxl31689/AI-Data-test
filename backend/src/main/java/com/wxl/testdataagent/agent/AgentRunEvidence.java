package com.wxl.testdataagent.agent;

import com.wxl.testdataagent.testrun.RunStatus;

import java.util.List;

record AgentRunEvidence(
        String runId,
        RunStatus status,
        int totalCount,
        int processedCount,
        int passCount,
        int failCount,
        int errorCount,
        int skippedCount,
        List<AgentFailureEvidence> failures,
        boolean truncated
) {
}
