package com.wxl.testdataagent.agent;

import com.wxl.testdataagent.testcase.AssertionConfig;
import com.wxl.testdataagent.testcase.CasePriority;
import com.wxl.testdataagent.testcase.ScenarioType;
import com.wxl.testdataagent.testcase.TestRequestConfig;

import java.util.List;

public record AgentTestCaseCandidate(
        String name,
        ScenarioType scenarioType,
        CasePriority priority,
        TestRequestConfig request,
        List<AssertionConfig> assertions,
        String rationale,
        String risk
) {
}
