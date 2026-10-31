package com.wxl.testdataagent.agent;

record AgentToolExecution(
        String output,
        AgentToolCallAudit audit,
        AgentRunEvidence evidence
) {
}
