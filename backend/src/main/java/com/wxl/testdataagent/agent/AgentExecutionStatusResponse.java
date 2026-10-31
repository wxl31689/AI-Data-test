package com.wxl.testdataagent.agent;

import com.wxl.testdataagent.testrun.TestRunResponse;

import java.time.Instant;

public record AgentExecutionStatusResponse(
        String agentTaskId,
        String approvalId,
        String projectId,
        AgentExecutionApprovalStatus approvalStatus,
        Instant approvedAt,
        Instant expiresAt,
        TestRunResponse run
) {
}
