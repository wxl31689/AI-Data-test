package com.wxl.testdataagent.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AcceptAgentTestCaseRequest(
        @NotBlank(message = "项目 ID 不能为空")
        String projectId,

        @NotBlank(message = "接口定义 ID 不能为空")
        String apiDefinitionId,

        @NotNull(message = "候选用例不能为空")
        @Valid
        AgentTestCaseCandidate candidate
) {
}
