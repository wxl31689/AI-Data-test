package com.wxl.testdataagent.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 120, message = "项目名称不能超过 120 个字符")
        String name,

        @Size(max = 1000, message = "项目描述不能超过 1000 个字符")
        String description
) {
}

