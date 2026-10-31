package com.wxl.testdataagent.agent;

import com.wxl.testdataagent.testrun.ResultStatus;

public record AgentRootCause(
        String testResultId,
        String caseName,
        ResultStatus deterministicStatus,
        AgentRootCauseCategory category,
        String evidence,
        String explanation,
        AgentConfidence confidence,
        String suggestedAction
) {
}
