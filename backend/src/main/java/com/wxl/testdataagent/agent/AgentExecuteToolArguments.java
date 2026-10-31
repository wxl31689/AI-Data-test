package com.wxl.testdataagent.agent;

import java.util.List;

record AgentExecuteToolArguments(
        String approvalId,
        String projectId,
        List<String> caseIds,
        String runName,
        String clientRequestId
) {
}
