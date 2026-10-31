package com.wxl.testdataagent.agent;

record AgentAssertionEvidence(
        String assertionType,
        String expected,
        String actual,
        String message
) {
}
