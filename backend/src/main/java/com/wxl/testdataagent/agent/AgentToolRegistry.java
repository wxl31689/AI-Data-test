package com.wxl.testdataagent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wxl.testdataagent.common.InvalidRequestException;
import org.springframework.stereotype.Component;

@Component
class AgentToolRegistry {

    static final String RUN_EVIDENCE_TOOL = "get_test_run_evidence";
    static final String EXECUTE_TEST_RUN_TOOL = "execute_test_run";
    private static final int MAX_ARGUMENT_CHARS = 4_000;
    private static final int MAX_OUTPUT_CHARS = 50_000;

    private final RunEvidenceProvider evidenceProvider;
    private final ObjectMapper objectMapper;

    AgentToolRegistry(RunEvidenceProvider evidenceProvider, ObjectMapper objectMapper) {
        this.evidenceProvider = evidenceProvider;
        this.objectMapper = objectMapper;
    }

    AgentToolExecution execute(AiToolCall call, String expectedRunId) {
        if (!RUN_EVIDENCE_TOOL.equals(call.name())) {
            throw new InvalidRequestException("模型请求了未授权工具: " + call.name());
        }
        if (call.arguments() == null || call.arguments().length() > MAX_ARGUMENT_CHARS) {
            throw new InvalidRequestException("模型工具参数超过安全限制");
        }
        JsonNode arguments;
        try {
            arguments = objectMapper.readTree(call.arguments());
        } catch (JsonProcessingException exception) {
            throw new InvalidRequestException("模型工具参数不是有效 JSON", exception);
        }
        if (arguments == null || !arguments.isObject() || arguments.size() != 1
                || !arguments.has("runId") || !arguments.path("runId").isTextual()) {
            throw new InvalidRequestException("模型工具参数不符合允许的结构");
        }
        String runId = arguments.path("runId").asText();
        if (!expectedRunId.equals(runId)) {
            throw new InvalidRequestException("模型工具参数尝试访问未授权测试任务");
        }
        AgentRunEvidence evidence = evidenceProvider.get(runId);
        try {
            String output = objectMapper.writeValueAsString(evidence);
            if (output.length() > MAX_OUTPUT_CHARS) {
                throw new InvalidRequestException("工具证据超过可发送给模型的大小限制");
            }
            return new AgentToolExecution(
                    output,
                    new AgentToolCallAudit(
                            call.name(),
                            call.callId(),
                            runId,
                            true,
                            "只读白名单工具；仅允许读取本次请求指定 runId 的脱敏持久化证据"
                    ),
                    evidence
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化工具证据", exception);
        }
    }

    AgentExecuteToolArguments validateExecute(
            AiToolCall call,
            AgentExecutionApprovalEntity approval,
            java.util.List<String> approvedCaseIds
    ) {
        if (!EXECUTE_TEST_RUN_TOOL.equals(call.name())) {
            throw new InvalidRequestException("模型请求了未授权工具: " + call.name());
        }
        JsonNode arguments = parseArguments(call.arguments());
        if (!arguments.isObject() || arguments.size() != 5
                || !text(arguments, "approvalId") || !text(arguments, "projectId")
                || !arguments.path("caseIds").isArray() || !text(arguments, "runName")
                || !text(arguments, "clientRequestId")) {
            throw new InvalidRequestException("模型执行工具参数不符合允许的结构");
        }
        java.util.List<String> caseIds = new java.util.ArrayList<>();
        for (JsonNode caseId : arguments.path("caseIds")) {
            if (!caseId.isTextual()) throw new InvalidRequestException("模型用例 ID 参数无效");
            caseIds.add(caseId.asText());
        }
        AgentExecuteToolArguments parsed = new AgentExecuteToolArguments(
                arguments.path("approvalId").asText(), arguments.path("projectId").asText(),
                java.util.List.copyOf(caseIds), arguments.path("runName").asText(),
                arguments.path("clientRequestId").asText()
        );
        if (!approval.getId().equals(parsed.approvalId())
                || !approval.getProjectId().equals(parsed.projectId())
                || !approvedCaseIds.equals(parsed.caseIds())
                || !approval.getRunName().equals(parsed.runName())
                || !approval.getRequestKey().equals(parsed.clientRequestId())) {
            throw new InvalidRequestException("模型执行工具参数与人工审批记录不一致");
        }
        if (approval.getStatus() != AgentExecutionApprovalStatus.APPROVED
                || approval.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new InvalidRequestException("执行审批已失效或已使用");
        }
        return parsed;
    }

    private JsonNode parseArguments(String raw) {
        if (raw == null || raw.length() > MAX_ARGUMENT_CHARS) {
            throw new InvalidRequestException("模型工具参数超过安全限制");
        }
        try {
            JsonNode parsed = objectMapper.readTree(raw);
            if (parsed == null) throw new InvalidRequestException("模型工具参数不能为空");
            return parsed;
        } catch (JsonProcessingException exception) {
            throw new InvalidRequestException("模型工具参数不是有效 JSON", exception);
        }
    }

    private boolean text(JsonNode node, String name) {
        return node.has(name) && node.path(name).isTextual() && !node.path(name).asText().isBlank();
    }
}
