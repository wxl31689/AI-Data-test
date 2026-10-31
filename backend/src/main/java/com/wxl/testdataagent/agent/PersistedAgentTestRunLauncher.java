package com.wxl.testdataagent.agent;

import com.wxl.testdataagent.testrun.TestRunResponse;
import com.wxl.testdataagent.testrun.TestRunService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class PersistedAgentTestRunLauncher implements AgentTestRunLauncher {

    private final TestRunService testRunService;

    PersistedAgentTestRunLauncher(TestRunService testRunService) {
        this.testRunService = testRunService;
    }

    @Override
    public TestRunResponse launch(String projectId, String runName, List<String> caseIds, String requestKey,
                                  java.util.Map<String, Integer> approvedVersions) {
        return testRunService.createAgentRun(projectId, runName, caseIds, requestKey, approvedVersions);
    }
}
