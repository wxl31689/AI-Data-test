package com.wxl.testdataagent.testcase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties({"source", "reviewStatus"})
public record CreateTestCaseRequest(
        @NotBlank(message = "项目 ID 不能为空")
        String projectId,

        @NotBlank(message = "接口定义 ID 不能为空")
        String apiDefinitionId,

        @NotBlank(message = "用例名称不能为空")
        @Size(max = 180, message = "用例名称不能超过 180 个字符")
        String name,

        @NotNull(message = "场景类型不能为空")
        ScenarioType scenarioType,

        @NotNull(message = "请求配置不能为空")
        @Valid
        TestRequestConfig request,

        @NotEmpty(message = "至少需要一个断言")
        List<@Valid AssertionConfig> assertions,

        @NotNull(message = "优先级不能为空")
        CasePriority priority
) {
}

