package com.wxl.testdataagent.agent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateAgentTestCasesRequest(
        @NotBlank(message = "项目 ID 不能为空")
        String projectId,

        @NotBlank(message = "接口定义 ID 不能为空")
        String apiDefinitionId,

        @Min(value = 1, message = "至少生成 1 条候选用例")
        @Max(value = 5, message = "单次最多生成 5 条候选用例")
        int count,

        @Size(max = 500, message = "生成重点不能超过 500 个字符")
        String focus
) {
}
