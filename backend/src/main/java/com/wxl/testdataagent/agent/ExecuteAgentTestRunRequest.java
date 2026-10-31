package com.wxl.testdataagent.agent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExecuteAgentTestRunRequest(
        @NotBlank(message = "项目 ID 不能为空")
        String projectId,

        @NotEmpty(message = "至少选择一个测试用例")
        @Size(max = 20, message = "单次最多执行 20 个测试用例")
        List<@NotBlank(message = "用例 ID 不能为空") String> caseIds,

        @NotBlank(message = "运行名称不能为空")
        @Size(max = 180, message = "运行名称不能超过 180 个字符")
        String runName,

        @NotBlank(message = "幂等键不能为空")
        @Size(max = 60, message = "幂等键不能超过 60 个字符")
        @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "幂等键包含不允许的字符")
        String clientRequestId,

        @Min(value = 100, message = "最大等待时间不能小于 100 毫秒")
        @Max(value = 5000, message = "最大等待时间不能超过 5000 毫秒")
        Integer maxWaitMillis
) {
}
