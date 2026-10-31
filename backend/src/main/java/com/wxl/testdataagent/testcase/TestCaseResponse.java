package com.wxl.testdataagent.testcase;

import java.time.Instant;
import java.util.List;

public record TestCaseResponse(
        String id,
        String projectId,
        String apiDefinitionId,
        String name,
        ScenarioType scenarioType,
        TestRequestConfig request,
        List<AssertionConfig> assertions,
        CasePriority priority,
        CaseSource source,
        ReviewStatus reviewStatus,
        int version,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}

