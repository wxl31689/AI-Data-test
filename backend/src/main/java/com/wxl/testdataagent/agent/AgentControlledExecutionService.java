package com.wxl.testdataagent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wxl.testdataagent.common.InvalidRequestException;
import com.wxl.testdataagent.common.ResourceNotFoundException;
import com.wxl.testdataagent.testrun.TestRunResponse;
import com.wxl.testdataagent.testrun.TestRunService;
import com.wxl.testdataagent.testcase.TestCaseEntity;
import com.wxl.testdataagent.testcase.TestCaseReviewRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AgentControlledExecutionService {

    private final AiStructuredClient aiClient;
    private final AgentSchemas schemas;
    private final AgentToolRegistry toolRegistry;
    private final AgentTestRunLauncher launcher;
    private final TestRunService testRunService;
    private final AgentExecutionApprovalRepository approvalRepository;
    private final AgentToolAuditRepository auditRepository;
    private final AgentToolAuditService auditService;
    private final ObjectMapper objectMapper;
    private final int configuredToolTimeoutMillis;
    private final TestCaseReviewRepository reviewRepository;

    public AgentControlledExecutionService(
            AiStructuredClient aiClient,
            AgentSchemas schemas,
            AgentToolRegistry toolRegistry,
            AgentTestRunLauncher launcher,
            TestRunService testRunService,
            AgentExecutionApprovalRepository approvalRepository,
            AgentToolAuditRepository auditRepository,
            AgentToolAuditService auditService,
            ObjectMapper objectMapper,
            @Value("${agent.tool.execution-timeout-ms:5000}") int configuredToolTimeoutMillis,
            TestCaseReviewRepository reviewRepository
    ) {
        this.aiClient = aiClient;
        this.schemas = schemas;
        this.toolRegistry = toolRegistry;
        this.launcher = launcher;
        this.testRunService = testRunService;
        this.approvalRepository = approvalRepository;
        this.auditRepository = auditRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.configuredToolTimeoutMillis = Math.max(100, Math.min(configuredToolTimeoutMillis, 5000));
        this.reviewRepository = reviewRepository;
    }

    public synchronized AgentExecutionResponse execute(ExecuteAgentTestRunRequest request) {
        validateRequest(request);
        String projectId = request.projectId().trim();
        String runName = request.runName().trim();
        String requestKey = request.clientRequestId().trim();
        List<String> caseIds = request.caseIds().stream().map(String::trim).toList();
        AgentExecutionApprovalEntity existing = approvalRepository.findByRequestKey(requestKey).orElse(null);
        if (existing != null) {
            verifySameApproval(existing, projectId, caseIds, runName);
            reconcile(existing);
            if (existing.getRunId() != null) {
                String auditId = auditRepository.findFirstByApprovalIdOrderByStartedAtDesc(existing.getId())
                        .map(AgentToolAuditEntity::getId).orElse(null);
                return new AgentExecutionResponse(
                        existing.getAgentTaskId(), existing.getId(), auditId, true,
                        "幂等请求已返回原测试任务；未创建重复任务。", testRunService.get(existing.getRunId())
                );
            }
            if (existing.getStatus() == AgentExecutionApprovalStatus.FAILED) {
                throw new InvalidRequestException("原执行授权已失败；请检查审计记录后重新授权");
            }
            String auditId = auditRepository.findFirstByApprovalIdOrderByStartedAtDesc(existing.getId())
                    .map(AgentToolAuditEntity::getId).orElse("");
            throw new AgentToolTimeoutException(existing.getId(), existing.getAgentTaskId(), auditId);
        }

        if (!aiClient.available()) {
            throw new AgentUnavailableException("AI Agent 未启用或模型凭据未配置");
        }
        List<TestCaseEntity> cases = testRunService.validateExecutableCases(projectId, caseIds);
        Map<String, Integer> caseVersions = new LinkedHashMap<>();
        List<String> reviewIds = new java.util.ArrayList<>();
        for (TestCaseEntity testCase : cases) {
            caseVersions.put(testCase.getId(), testCase.getVersion());
            var reviews = reviewRepository.findByTestCaseIdAndCaseVersionOrderByReviewedAtDesc(
                    testCase.getId(), testCase.getVersion());
            if (reviews.isEmpty()) {
                throw new InvalidRequestException("用例缺少当前版本的人工审核记录，请重新审核: " + testCase.getId());
            }
            reviewIds.add(reviews.getFirst().getId());
        }

        AgentExecutionApprovalEntity approval = existing;
        if (approval == null) {
            try {
                approval = approvalRepository.save(new AgentExecutionApprovalEntity(
                        projectId, writeJson(caseIds), runName, requestKey,
                        Instant.now().plus(10, ChronoUnit.MINUTES)));
            } catch (DataIntegrityViolationException exception) {
                throw new InvalidRequestException("相同幂等请求正在受理，请查询已有审批状态", exception);
            }
        }
        approval.bindCaseVersions(writeJson(caseVersions));
        approval.bindReviews(writeJson(reviewIds));
        approvalRepository.save(approval);

        ObjectNode input = objectMapper.createObjectNode();
        input.put("approvalId", approval.getId());
        input.put("projectId", projectId);
        input.set("caseIds", objectMapper.valueToTree(caseIds));
        input.put("runName", runName);
        input.put("clientRequestId", requestKey);
        input.put("constraint", "Copy the approved fields exactly and call execute_test_run once.");

        String auditId = auditService.start(
                approval.getAgentTaskId(), AgentToolRegistry.EXECUTE_TEST_RUN_TOOL,
                projectId, null, approval.getId(), writeJson(input));
        AiToolCall call;
        try {
            call = aiClient.requestToolCall(
                """
                你是受控测试执行 Agent。只能调用 execute_test_run 一次，并逐字复制服务端提供的审批字段。
                不得修改 projectId、caseIds、runName、clientRequestId 或 approvalId；不得加入主机、URL、SQL、命令、文件路径或审批字段。
                工具只创建异步测试任务，PASS/FAIL 最终状态只能由确定性断言引擎产生。
                """,
                writeJson(input),
                AgentToolRegistry.EXECUTE_TEST_RUN_TOOL,
                "基于有效人工审批，通过现有测试运行服务创建异步测试任务",
                schemas.executeTestRunToolParameters()
            );
        } catch (RuntimeException exception) {
            approval.markFailed();
            approvalRepository.save(approval);
            auditService.failure(auditId, AgentToolAuditStatus.FAILED,
                    exception.getClass().getSimpleName(), "模型调用失败，工具未执行");
            throw exception;
        }
        AgentExecuteToolArguments arguments;
        try {
            arguments = toolRegistry.validateExecute(call, approval, caseIds);
        } catch (RuntimeException exception) {
            approval.markFailed();
            approvalRepository.save(approval);
            auditService.failure(auditId, AgentToolAuditStatus.FAILED,
                    exception.getClass().getSimpleName(), "模型工具参数未通过服务端审批约束校验");
            throw exception;
        }

        int requestedWait = request.maxWaitMillis() == null ? configuredToolTimeoutMillis : request.maxWaitMillis();
        int timeoutMillis = Math.min(configuredToolTimeoutMillis, requestedWait);
        FutureTask<TestRunResponse> task = new FutureTask<>(() -> launcher.launch(
                arguments.projectId(), arguments.runName(), arguments.caseIds(), "agent:" + arguments.clientRequestId(),
                Map.copyOf(caseVersions)));
        Thread worker = Thread.ofVirtual().name("agent-execute-test-run").start(task);
        try {
            TestRunResponse run = task.get(timeoutMillis, TimeUnit.MILLISECONDS);
            approval.markExecuted(run.id());
            approvalRepository.save(approval);
            auditService.success(auditId, run.id(),
                    "异步测试任务已通过 TestRunService 创建；状态=" + run.status() + "，runId=" + run.id());
            String acknowledgement = continueToolLoop(call, approval, run);
            return new AgentExecutionResponse(
                    approval.getAgentTaskId(), approval.getId(), auditId, false, acknowledgement, run);
        } catch (TimeoutException exception) {
            task.cancel(true);
            worker.interrupt();
            approval.markTimedOut();
            approvalRepository.save(approval);
            auditService.failure(auditId, AgentToolAuditStatus.TIMED_OUT,
                    TimeoutException.class.getSimpleName(), "受控工具在最大等待时间内未完成任务创建");
            throw new AgentToolTimeoutException(approval.getId(), approval.getAgentTaskId(), auditId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            approval.markFailed();
            approvalRepository.save(approval);
            auditService.failure(auditId, AgentToolAuditStatus.FAILED,
                    InterruptedException.class.getSimpleName(), "受控工具线程被中断");
            throw new InvalidRequestException("Agent 受控执行被中断", exception);
        } catch (ExecutionException exception) {
            approval.markFailed();
            approvalRepository.save(approval);
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            auditService.failure(auditId, AgentToolAuditStatus.FAILED,
                    cause.getClass().getSimpleName(), "测试任务创建失败");
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new InvalidRequestException("Agent 受控执行失败", cause);
        }
    }

    public AgentExecutionStatusResponse getStatus(String approvalId) {
        AgentExecutionApprovalEntity approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent 执行审批不存在"));
        reconcile(approval);
        TestRunResponse run = approval.getRunId() == null ? null : testRunService.get(approval.getRunId());
        return new AgentExecutionStatusResponse(
                approval.getAgentTaskId(), approval.getId(), approval.getProjectId(), approval.getStatus(),
                approval.getCreatedAt(), approval.getExpiresAt(), run
        );
    }

    private void reconcile(AgentExecutionApprovalEntity approval) {
        if (approval.getRunId() != null) return;
        TestRunResponse run = testRunService.findByRequestKey("agent:" + approval.getRequestKey());
        if (run == null) return;
        approval.markExecuted(run.id());
        approvalRepository.save(approval);
        auditRepository.findFirstByApprovalIdOrderByStartedAtDesc(approval.getId())
                .ifPresent(audit -> auditService.linkRun(audit.getId(), run.id()));
    }

    private void validateRequest(ExecuteAgentTestRunRequest request) {
        if (request == null || request.projectId() == null || request.projectId().isBlank()
                || request.caseIds() == null || request.caseIds().isEmpty() || request.caseIds().size() > 20
                || request.caseIds().stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 36)
                || request.runName() == null || request.runName().isBlank() || request.runName().trim().length() > 180
                || request.clientRequestId() == null || !request.clientRequestId().matches("[A-Za-z0-9._:-]{1,60}")
                || (request.maxWaitMillis() != null && (request.maxWaitMillis() < 100 || request.maxWaitMillis() > 5000))) {
            throw new InvalidRequestException("Agent 执行请求参数无效");
        }
    }

    private String continueToolLoop(
            AiToolCall call,
            AgentExecutionApprovalEntity approval,
            TestRunResponse run
    ) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("agentTaskId", approval.getAgentTaskId());
        output.put("approvalId", approval.getId());
        output.put("runId", run.id());
        output.put("status", run.status().name());
        try {
            JsonNode acknowledgement = aiClient.continueStructured(
                    call, writeJson(output),
                    "工具已经创建异步任务。只能确认 runId 和当前状态，不得宣称测试通过或失败。",
                    "test_run_execution_acknowledgement", schemas.executionAcknowledgement());
            if (!run.id().equals(acknowledgement.path("runId").asText())) {
                return "模型确认内容与真实任务不一致，已忽略；请以持久化任务状态为准。";
            }
            String summary = acknowledgement.path("summary").asText().trim();
            return summary.length() <= 500 ? summary : summary.substring(0, 500);
        } catch (RuntimeException exception) {
            return "测试任务已创建；模型确认步骤失败，请以持久化任务状态为准。";
        }
    }

    private void verifySameApproval(
            AgentExecutionApprovalEntity approval,
            String projectId,
            List<String> caseIds,
            String runName
    ) {
        if (!approval.getProjectId().equals(projectId)
                || !approval.getRunName().equals(runName)
                || !approval.getCaseIdsJson().equals(writeJson(caseIds))) {
            throw new InvalidRequestException("幂等键已用于不同的 Agent 执行请求");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法构造 Agent 受控执行数据", exception);
        }
    }
}
