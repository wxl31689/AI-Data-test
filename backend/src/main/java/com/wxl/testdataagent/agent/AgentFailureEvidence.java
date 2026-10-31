package com.wxl.testdataagent.agent;

import com.wxl.testdataagent.testrun.ResultStatus;

import java.util.List;

record AgentFailureEvidence(
        String testResultId,
        String caseName,
        ResultStatus deterministicStatus,
        Integer statusCode,
        Long durationMs,
        String errorMessage,
        String requestSummary,
        String responseSummary,
        List<AgentAssertionEvidence> failedAssertions
) {
}
