package com.wxl.testdataagent.testrun;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wxl.testdataagent.apidefinition.ApiDefinitionEntity;
import com.wxl.testdataagent.apidefinition.ApiDefinitionRepository;
import com.wxl.testdataagent.common.InvalidRequestException;
import com.wxl.testdataagent.common.ResourceNotFoundException;
import com.wxl.testdataagent.testcase.ReviewStatus;
import com.wxl.testdataagent.testcase.TestCaseEntity;
import com.wxl.testdataagent.testcase.TestCaseResponse;
import com.wxl.testdataagent.testcase.TestCaseService;
import com.wxl.testdataagent.testcase.TestRequestCredentialProtector;
import com.wxl.testdataagent.testengine.AssertionEngine;
import com.wxl.testdataagent.testengine.AssertionEvaluation;
import com.wxl.testdataagent.testengine.HttpExecutionResult;
import com.wxl.testdataagent.testengine.HttpTestExecutor;
import com.wxl.testdataagent.testengine.EvidenceSanitizer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TestRunExecutionWorker {

    private final TestRunRepository testRunRepository;
    private final TestResultRepository testResultRepository;
    private final AssertionResultRepository assertionResultRepository;
    private final ApiDefinitionRepository apiDefinitionRepository;
    private final TestCaseService testCaseService;
    private final HttpTestExecutor httpTestExecutor;
    private final AssertionEngine assertionEngine;
    private final ObjectMapper objectMapper;
    private final TestRequestCredentialProtector credentialProtector;
    private final EvidenceSanitizer sanitizer;

    public TestRunExecutionWorker(
            TestRunRepository testRunRepository,
            TestResultRepository testResultRepository,
            AssertionResultRepository assertionResultRepository,
            ApiDefinitionRepository apiDefinitionRepository,
            TestCaseService testCaseService,
            HttpTestExecutor httpTestExecutor,
            AssertionEngine assertionEngine,
            ObjectMapper objectMapper,
            TestRequestCredentialProtector credentialProtector,
            EvidenceSanitizer sanitizer
    ) {
        this.testRunRepository = testRunRepository;
        this.testResultRepository = testResultRepository;
        this.assertionResultRepository = assertionResultRepository;
        this.apiDefinitionRepository = apiDefinitionRepository;
        this.testCaseService = testCaseService;
        this.httpTestExecutor = httpTestExecutor;
        this.assertionEngine = assertionEngine;
        this.objectMapper = objectMapper;
        this.credentialProtector = credentialProtector;
        this.sanitizer = sanitizer;
    }

    @Async("testRunTaskExecutor")
    public void execute(String runId, List<String> caseIds) {
        executeApproved(runId, caseIds, java.util.Map.of());
    }

    @Async("testRunTaskExecutor")
    public void executeApproved(String runId, List<String> caseIds, java.util.Map<String, Integer> approvedVersions) {
        try {
            for (String caseId : caseIds) {
                TestCaseEntity testCase = testCaseService.findEntity(caseId);
                ResultStatus resultStatus = executeCase(runId, testCase, approvedVersions.get(caseId));
                TestRunEntity run = findRun(runId);
                run.record(resultStatus);
                testRunRepository.save(run);
            }
            TestRunEntity run = findRun(runId);
            run.complete();
            testRunRepository.save(run);
        } catch (Exception exception) {
            TestRunEntity run = findRun(runId);
            run.fail();
            testRunRepository.save(run);
        }
    }

    private ResultStatus executeCase(String runId, TestCaseEntity testCase, Integer approvedVersion) {
        TestCaseResponse snapshot = testCaseService.snapshot(testCase);
        String snapshotJson = writeJson(snapshot);

        if (!testCase.isEnabled() || testCase.getReviewStatus() != ReviewStatus.REVIEWED
                || testCase.getReviewRecordId() == null
                || (approvedVersion != null && testCase.getVersion() != approvedVersion)) {
            TestResultEntity skipped = testResultRepository.save(new TestResultEntity(
                    runId, testCase.getId(), testCase.getName(), snapshotJson,
                    null, null, null, null, ResultStatus.SKIPPED,
                    "用例未启用、尚未审核或已偏离授权版本"
            ));
            return skipped.getResultStatus();
        }

        try {
            ApiDefinitionEntity definition = apiDefinitionRepository.findById(testCase.getApiDefinitionId())
                    .orElseThrow(() -> new ResourceNotFoundException("接口定义不存在"));
            if (!definition.isEnabled()) throw new InvalidRequestException("接口定义已停用");
            HttpExecutionResult httpResult = httpTestExecutor.execute(
                    definition,
                    credentialProtector.resolveForExecution(testCaseService.readRequest(testCase))
            );
            var assertionConfigs = testCaseService.readAssertions(testCase);
            List<AssertionEvaluation> evaluations = assertionEngine.evaluate(
                    httpResult.statusCode(),
                    httpResult.responseBody(),
                    httpResult.durationMs(),
                    assertionConfigs
            );
            ResultStatus resultStatus = evaluations.stream().allMatch(AssertionEvaluation::passed)
                    ? ResultStatus.PASS
                    : ResultStatus.FAIL;
            TestResultEntity result = testResultRepository.save(new TestResultEntity(
                    runId, testCase.getId(), testCase.getName(), snapshotJson,
                    httpResult.requestSummary(), httpResult.responseSummary(),
                    httpResult.statusCode(), httpResult.durationMs(), resultStatus, null
            ));
            saveAssertionResults(result.getId(), evaluations, assertionConfigs);
            return resultStatus;
        } catch (Exception exception) {
            TestResultEntity result = testResultRepository.save(new TestResultEntity(
                    runId, testCase.getId(), testCase.getName(), snapshotJson,
                    null, null, null, null, ResultStatus.ERROR, safeErrorMessage(exception)
            ));
            return result.getResultStatus();
        }
    }

    private void saveAssertionResults(String testResultId, List<AssertionEvaluation> evaluations,
                                     List<com.wxl.testdataagent.testcase.AssertionConfig> configs) {
        assertionResultRepository.saveAll(java.util.stream.IntStream.range(0, evaluations.size())
                .mapToObj(index -> {
                    AssertionEvaluation evaluation = evaluations.get(index);
                    boolean sensitive = sanitizer.isSensitive(configs.get(index).path());
                    return new AssertionResultEntity(
                        testResultId,
                        evaluation.type(),
                        sensitive ? "***" : sanitizer.sanitizeResponse(evaluation.expected(), 4000),
                        sensitive ? "***" : sanitizer.sanitizeResponse(evaluation.actual(), 4000),
                        evaluation.passed(),
                        sensitive ? "敏感字段断言证据已脱敏" : sanitizer.sanitizeResponse(evaluation.message(), 1000)
                    );
                })
                .toList());
    }

    private TestRunEntity findRun(String id) {
        return testRunRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("测试任务不存在"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存用例快照", exception);
        }
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception instanceof InvalidRequestException
                ? exception.getMessage()
                : "HTTP 请求执行失败: " + exception.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
