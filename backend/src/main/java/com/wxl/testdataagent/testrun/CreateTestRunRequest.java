package com.wxl.testdataagent.testrun;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateTestRunRequest(
        @NotBlank(message = "项目 ID 不能为空")
        String projectId,

        @Size(max = 180, message = "运行名称不能超过 180 个字符")
        String runName,

        @NotEmpty(message = "至少选择一个测试用例")
        @Size(max = 20, message = "单次最多执行 20 个测试用例")
        List<@NotBlank(message = "用例 ID 不能为空") String> caseIds,

        @Size(max = 80, message = "幂等键不能超过 80 个字符")
        String clientRequestId
) {
}

