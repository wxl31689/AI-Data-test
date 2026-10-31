package com.wxl.testdataagent.testrun;

import com.wxl.testdataagent.common.InvalidRequestException;
import com.wxl.testdataagent.common.ResourceNotFoundException;
import com.wxl.testdataagent.project.ProjectRepository;
import com.wxl.testdataagent.apidefinition.ApiDefinitionEntity;
import com.wxl.testdataagent.apidefinition.ApiDefinitionRepository;
import com.wxl.testdataagent.testcase.ReviewStatus;
import com.wxl.testdataagent.testcase.TestCaseEntity;
import com.wxl.testdataagent.testcase.TestCaseService;
import com.wxl.testdataagent.testengine.RegisteredTargetPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TestRunService {

    private final TestRunRepository testRunRepository;
    private final TestResultRepository testResultRepository;
    private final AssertionResultRepository assertionResultRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseService testCaseService;
    private final TestRunExecutionWorker executionWorker;
    private final ApiDefinitionRepository apiDefinitionRepository;
    private final RegisteredTargetPolicy registeredTargetPolicy;
    private final int maxCasesPerRun;

    public TestRunService(
            TestRunRepository testRunRepository,
            TestResultRepository testResultRepository,
            AssertionResultRepository assertionResultRepository,
            ProjectRepository projectRepository,
            TestCaseService testCaseService,
            TestRunExecutionWorker executionWorker,
            ApiDefinitionRepository apiDefinitionRepository,
            RegisteredTargetPolicy registeredTargetPolicy,
            @Value("${test-engine.max-cases-per-run}") int maxCasesPerRun
    ) {
        this.testRunRepository = testRunRepository;
        this.testResultRepository = testResultRepository;
        this.assertionResultRepository = assertionResultRepository;
        this.projectRepository = projectRepository;
        this.testCaseService = testCaseService;
        this.executionWorker = executionWorker;
        this.apiDefinitionRepository = apiDefinitionRepository;
        this.registeredTargetPolicy = registeredTargetPolicy;
        this.maxCasesPerRun = maxCasesPerRun;
    }

    public TestRunResponse createAgentRun(
            String projectId,
            String runName,
            List<String> caseIds,
            String requestKey
    ) {
        return createRun(projectId, runName, caseIds, TriggerType.AGENT, null, requestKey);
    }

    public synchronized TestRunResponse createAgentRun(
            String projectId, String runName, List<String> caseIds, String requestKey,
            java.util.Map<String, Integer> approvedVersions
    ) {
        List<TestCaseEntity> cases = validateExecutableCases(projectId, caseIds);
        if (cases.stream().anyMatch(item -> !java.util.Objects.equals(
                approvedVersions.get(item.getId()), item.getVersion()))) {
            throw new InvalidRequestException("用例版本已发生变化，必须重新授权执行");
        }
        return createRun(projectId, runName, caseIds, TriggerType.AGENT, null, requestKey);
    }

    public List<TestCaseEntity> validateExecutableCases(String projectId, List<String> requestedCaseIds) {
        if (projectId == null || projectId.isBlank()) {
            throw new InvalidRequestException("项目 ID 不能为空");
        }
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("项目不存在");
        }
        if (requestedCaseIds == null || requestedCaseIds.isEmpty()
                || requestedCaseIds.size() > maxCasesPerRun) {
            throw new InvalidRequestException("单次执行用例数量必须在 1–" + maxCasesPerRun + " 之间");
        }
        if (requestedCaseIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new InvalidRequestException("用例 ID 不能为空");
        }
        List<String> caseIds = requestedCaseIds.stream().distinct().toList();
        if (caseIds.size() != requestedCaseIds.size()) {
            throw new InvalidRequestException("用例 ID 不能重复");
        }
        List<TestCaseEntity> cases = caseIds.stream().map(testCaseService::findEntity).toList();
        for (TestCaseEntity testCase : cases) {
            if (!testCase.getProjectId().equals(projectId)) {
                throw new InvalidRequestException("所选用例不全部属于当前项目");
            }
            if (!testCase.isEnabled() || testCase.getReviewStatus() != ReviewStatus.REVIEWED
                    || testCase.getReviewRecordId() == null) {
                throw new InvalidRequestException("用例未启用或尚未审核，不能创建执行任务: " + testCase.getId());
            }
            ApiDefinitionEntity definition = apiDefinitionRepository.findById(testCase.getApiDefinitionId())
                    .orElseThrow(() -> new ResourceNotFoundException("接口定义不存在"));
            if (!definition.isEnabled()) {
                throw new InvalidRequestException("接口定义已停用，不能执行用例: " + testCase.getId());
            }
            registeredTargetPolicy.validate(definition.getBaseUrl(), definition.getPath());
        }
        return cases;
    }

    public TestRunResponse create(CreateTestRunRequest request) {
        if (request.clientRequestId() != null && request.clientRequestId().trim().startsWith("agent:")) {
            throw new InvalidRequestException("该幂等键前缀仅供受控 Agent 工具使用");
        }
        return createRun(
                request.projectId(),
                request.runName(),
                request.caseIds(),
                TriggerType.MANUAL,
                null,
                request.clientRequestId()
        );
    }

    public TestRunResponse rerunFailed(String runId, RerunFailedRequest request) {
        TestRunEntity original = findRun(runId);
        if (original.getStatus() == RunStatus.RUNNING) {
            throw new InvalidRequestException("原任务仍在执行，暂不能重跑");
        }
        List<String> caseIds = testResultRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(result -> result.getResultStatus() == ResultStatus.FAIL
                        || result.getResultStatus() == ResultStatus.ERROR)
                .map(TestResultEntity::getTestCaseId)
                .distinct()
                .toList();
        if (caseIds.isEmpty()) {
            throw new InvalidRequestException("原任务没有可重跑的失败或错误用例");
        }
        return createRun(
                original.getProjectId(),
                "失败重跑 - " + original.getRunName(),
                caseIds,
                TriggerType.RERUN,
                original.getId(),
                request == null ? null : request.clientRequestId()
        );
    }

    @Transactional(readOnly = true)
    public List<TestRunResponse> list(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("项目不存在");
        }
        return testRunRepository.findByProjectIdOrderByStartedAtDesc(projectId).stream()
                .map(run -> toResponse(run, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public TestRunResponse get(String id) {
        return toResponse(findRun(id), true);
    }

    @Transactional(readOnly = true)
    public List<TestResultResponse> getResults(String id) {
        findRun(id);
        return buildResults(id);
    }

    private synchronized TestRunResponse createRun(
            String projectId,
            String runNameValue,
            List<String> requestedCaseIds,
            TriggerType triggerType,
            String parentRunId,
            String requestKeyValue
    ) {
        if (runNameValue != null && runNameValue.trim().length() > 180) {
            throw new InvalidRequestException("运行名称不能超过 180 个字符");
        }
        String requestKey = normalizeRequestKey(requestKeyValue);
        String fingerprint = fingerprint(projectId, runNameValue, requestedCaseIds, triggerType);
        if (requestKey != null) {
            TestRunEntity existing = testRunRepository.findByRequestKey(requestKey).orElse(null);
            if (existing != null) {
                if (!existing.getProjectId().equals(projectId)) {
                    throw new InvalidRequestException("幂等键已被其他项目使用");
                }
                if (!fingerprint.equals(existing.getRequestFingerprint())) {
                    throw new InvalidRequestException("幂等键已用于不同的测试执行请求");
                }
                return toResponse(existing, existing.getStatus() != RunStatus.RUNNING);
            }
        }
        List<TestCaseEntity> cases = validateExecutableCases(projectId, requestedCaseIds);
        List<String> caseIds = cases.stream().map(TestCaseEntity::getId).toList();

        String runName = runNameValue == null || runNameValue.isBlank()
                ? "手动执行 " + Instant.now()
                : runNameValue.trim();
        TestRunEntity run = new TestRunEntity(
                projectId, runName, triggerType, cases.size(), parentRunId, requestKey
        );
        run.setRequestFingerprint(fingerprint);
        run = testRunRepository.save(run);
        executionWorker.executeApproved(run.getId(), caseIds, cases.stream().collect(
                java.util.stream.Collectors.toMap(TestCaseEntity::getId, TestCaseEntity::getVersion)));
        return toResponse(run, false);
    }

    private String fingerprint(String projectId, String runName, List<String> caseIds, TriggerType triggerType) {
        if (caseIds == null || caseIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new InvalidRequestException("用例 ID 不能为空");
        }
        String content = projectId + "\n" + (runName == null ? "" : runName.trim())
                + "\n" + triggerType + "\n" + String.join(",", caseIds.stream().sorted().toList());
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public TestRunResponse findByRequestKey(String requestKey) {
        return testRunRepository.findByRequestKey(requestKey)
                .map(run -> toResponse(run, run.getStatus() != RunStatus.RUNNING)).orElse(null);
    }

    private String normalizeRequestKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) return null;
        String normalized = requestKey.trim();
        if (normalized.length() > 80 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new InvalidRequestException("幂等键只能包含字母、数字、点、下划线、冒号和连字符，且不能超过 80 个字符");
        }
        return normalized;
    }

    private TestRunEntity findRun(String id) {
        return testRunRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("测试任务不存在"));
    }

    private TestRunResponse toResponse(TestRunEntity run, boolean includeResults) {
        return new TestRunResponse(
                run.getId(), run.getProjectId(), run.getRunName(), run.getTriggerType(), run.getStatus(),
                run.getTotalCount(), run.getProcessedCount(), run.getPassCount(), run.getFailCount(),
                run.getErrorCount(), run.getSkippedCount(), run.getStartedAt(), run.getFinishedAt(),
                run.getParentRunId(), includeResults ? buildResults(run.getId()) : List.of()
        );
    }

    private List<TestResultResponse> buildResults(String runId) {
        List<TestResultResponse> responses = new ArrayList<>();
        for (TestResultEntity result : testResultRepository.findByRunIdOrderByCreatedAtAsc(runId)) {
            List<AssertionResultResponse> assertions = assertionResultRepository
                    .findByTestResultIdOrderByCreatedAtAsc(result.getId()).stream()
                    .map(AssertionResultResponse::from)
                    .toList();
            responses.add(new TestResultResponse(
                    result.getId(), result.getTestCaseId(), result.getCaseName(),
                    result.getRequestSummary(), result.getResponseSummary(), result.getStatusCode(),
                    result.getDurationMs(), result.getResultStatus(), result.getErrorMessage(),
                    result.getCreatedAt(), assertions
            ));
        }
        return List.copyOf(responses);
    }
}
