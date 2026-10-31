package com.wxl.testdataagent.agent;

public record AgentToolCallAudit(
        String name,
        String callId,
        String runId,
        boolean approved,
        String policy
) {
}
