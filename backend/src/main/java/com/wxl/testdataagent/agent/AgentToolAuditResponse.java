package com.wxl.testdataagent.agent;

import java.time.Instant;

public record AgentToolAuditResponse(
        String id,
        String agentTaskId,
        String toolName,
        String projectId,
        String runId,
        String approvalId,
        String inputSummary,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        AgentToolAuditStatus status,
        String resultSummary,
        String errorType
) {
}
