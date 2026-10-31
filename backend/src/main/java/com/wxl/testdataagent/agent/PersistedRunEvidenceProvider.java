package com.wxl.testdataagent.agent;

import com.wxl.testdataagent.testengine.EvidenceSanitizer;
import com.wxl.testdataagent.testrun.ResultStatus;
import com.wxl.testdataagent.testrun.RunStatus;
import com.wxl.testdataagent.testrun.TestResultResponse;
import com.wxl.testdataagent.testrun.TestRunResponse;
import com.wxl.testdataagent.testrun.TestRunService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class PersistedRunEvidenceProvider implements RunEvidenceProvider {

    private static final int MAX_FAILURES = 10;
    private static final int MAX_EVIDENCE_CHARS = 2_000;

    private final TestRunService testRunService;
    private final EvidenceSanitizer evidenceSanitizer;

    PersistedRunEvidenceProvider(
            TestRunService testRunService,
            EvidenceSanitizer evidenceSanitizer
    ) {
        this.testRunService = testRunService;
        this.evidenceSanitizer = evidenceSanitizer;
    }

    @Override
    public AgentRunEvidence get(String runId) {
        TestRunResponse run = testRunService.get(runId);
        if (run.status() == RunStatus.RUNNING) {
            throw new com.wxl.testdataagent.common.InvalidRequestException("测试任务仍在运行，暂不能分析");
        }
        List<TestResultResponse> allFailures = run.results().stream()
                .filter(result -> result.resultStatus() == ResultStatus.FAIL
                        || result.resultStatus() == ResultStatus.ERROR)
                .toList();
        List<AgentFailureEvidence> failures = allFailures.stream()
                .limit(MAX_FAILURES)
                .map(this::toEvidence)
                .toList();
        return new AgentRunEvidence(
                run.id(),
                run.status(),
                run.totalCount(),
                run.processedCount(),
                run.passCount(),
                run.failCount(),
                run.errorCount(),
                run.skippedCount(),
                failures,
                allFailures.size() > MAX_FAILURES
        );
    }

    private AgentFailureEvidence toEvidence(TestResultResponse result) {
        List<AgentAssertionEvidence> failedAssertions = result.assertions().stream()
                .filter(assertion -> !assertion.passed())
                .limit(10)
                .map(assertion -> new AgentAssertionEvidence(
                        assertion.assertionType(),
                        bounded(assertion.expectedValue()),
                        bounded(assertion.actualValue()),
                        bounded(assertion.message())
                ))
                .toList();
        return new AgentFailureEvidence(
                result.id(),
                bounded(result.caseName()),
                result.resultStatus(),
                result.statusCode(),
                result.durationMs(),
                bounded(result.errorMessage()),
                sanitizeSummary(result.requestSummary()),
                sanitizeSummary(result.responseSummary()),
                failedAssertions
        );
    }

    private String sanitizeSummary(String value) {
        return value == null ? null : evidenceSanitizer.sanitizeResponse(value, MAX_EVIDENCE_CHARS);
    }

    private String bounded(String value) {
        return value == null ? null : evidenceSanitizer.sanitizeResponse(value, MAX_EVIDENCE_CHARS);
    }
}
