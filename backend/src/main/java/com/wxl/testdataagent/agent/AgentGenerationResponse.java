package com.wxl.testdataagent.agent;

import java.time.Instant;
import java.util.List;

public record AgentGenerationResponse(
        String projectId,
        String apiDefinitionId,
        String model,
        List<AgentTestCaseCandidate> candidates,
        String reviewNotice,
        Instant generatedAt
) {
}
