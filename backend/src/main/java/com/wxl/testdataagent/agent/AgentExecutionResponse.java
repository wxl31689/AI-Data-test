package com.wxl.testdataagent.agent;

import com.wxl.testdataagent.testrun.TestRunResponse;

public record AgentExecutionResponse(
        String agentTaskId,
        String approvalId,
        String auditId,
        boolean idempotentReplay,
        String modelAcknowledgement,
        TestRunResponse run
) {
}
