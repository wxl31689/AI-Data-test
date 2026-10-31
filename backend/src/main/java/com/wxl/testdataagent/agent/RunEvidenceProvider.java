package com.wxl.testdataagent.agent;

interface RunEvidenceProvider {
    AgentRunEvidence get(String runId);
}
