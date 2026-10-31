package com.wxl.testdataagent.agent;

import com.wxl.testdataagent.common.ApiResponse;
import com.wxl.testdataagent.common.RequestTrace;
import com.wxl.testdataagent.common.LocalApprovalGuard;
import com.wxl.testdataagent.testcase.TestCaseResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AiStructuredClient aiClient;
    private final AgentTestCaseService testCaseService;
    private final AgentFailureAnalysisService failureAnalysisService;
    private final AgentControlledExecutionService controlledExecutionService;
    private final AgentToolAuditService auditService;
    private final LocalApprovalGuard approvalGuard;

    public AgentController(
            AiStructuredClient aiClient,
            AgentTestCaseService testCaseService,
            AgentFailureAnalysisService failureAnalysisService,
            AgentControlledExecutionService controlledExecutionService,
            AgentToolAuditService auditService,
            LocalApprovalGuard approvalGuard
    ) {
        this.aiClient = aiClient;
        this.testCaseService = testCaseService;
        this.failureAnalysisService = failureAnalysisService;
        this.controlledExecutionService = controlledExecutionService;
        this.auditService = auditService;
        this.approvalGuard = approvalGuard;
    }

    @GetMapping("/status")
    public ApiResponse<AgentStatusResponse> status(HttpServletRequest request) {
        AgentStatusResponse status = new AgentStatusResponse(
                aiClient.enabled(),
                aiClient.configured(),
                "OpenAI Responses API",
                aiClient.model(),
                List.of("结构化候选用例生成", "人工审核", "受控测试执行", "持久化工具审计", "真实失败证据分析"),
                "Agent 不产生或覆盖 PASS / FAIL / ERROR / SKIPPED"
        );
        return ApiResponse.ok(status, RequestTrace.get(request));
    }

    @PostMapping("/test-runs/execute")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AgentExecutionResponse> executeTestRun(
            @Valid @RequestBody ExecuteAgentTestRunRequest body,
            @RequestHeader(value = LocalApprovalGuard.HEADER, required = false) String approvalToken,
            HttpServletRequest request
    ) {
        approvalGuard.requireApproval(request, approvalToken);
        return ApiResponse.ok(controlledExecutionService.execute(body), RequestTrace.get(request));
    }

    @GetMapping("/executions/{approvalId}")
    public ApiResponse<AgentExecutionStatusResponse> executionStatus(
            @PathVariable String approvalId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(controlledExecutionService.getStatus(approvalId), RequestTrace.get(request));
    }

    @GetMapping("/audits")
    public ApiResponse<List<AgentToolAuditResponse>> audits(
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(auditService.list(projectId, limit), RequestTrace.get(request));
    }

    @PostMapping("/test-cases/generate")
    public ApiResponse<AgentGenerationResponse> generateTestCases(
            @Valid @RequestBody GenerateAgentTestCasesRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(testCaseService.generate(body), RequestTrace.get(request));
    }

    @PostMapping("/test-cases/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TestCaseResponse> acceptTestCase(
            @Valid @RequestBody AcceptAgentTestCaseRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.created(testCaseService.accept(body), RequestTrace.get(request));
    }

    @PostMapping("/runs/{runId}/analyze")
    public ApiResponse<AgentFailureAnalysisResponse> analyzeFailure(
            @PathVariable String runId,
            @Valid @RequestBody(required = false) AnalyzeAgentFailureRequest body,
            HttpServletRequest request
    ) {
        AnalyzeAgentFailureRequest safeBody = body == null
                ? new AnalyzeAgentFailureRequest(null)
                : body;
        return ApiResponse.ok(
                failureAnalysisService.analyze(runId, safeBody),
                RequestTrace.get(request)
        );
    }
}
