package com.wxl.testdataagent.agent;

import com.wxl.testdataagent.testrun.TestRunResponse;

import java.util.List;

interface AgentTestRunLauncher {
    TestRunResponse launch(String projectId, String runName, List<String> caseIds, String requestKey,
                           java.util.Map<String, Integer> approvedVersions);
}
