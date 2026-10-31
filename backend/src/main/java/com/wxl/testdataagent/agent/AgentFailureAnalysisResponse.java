package com.wxl.testdataagent.agent;

import java.time.Instant;
import java.util.List;

public record AgentFailureAnalysisResponse(
        String runId,
        String model,
        AgentDeterministicSummary deterministicSummary,
        String summary,
        List<AgentRootCause> rootCauses,
        List<String> recommendations,
        List<String> limitations,
        List<AgentToolCallAudit> toolCalls,
        String verdictPolicy,
        Instant generatedAt
) {
}
