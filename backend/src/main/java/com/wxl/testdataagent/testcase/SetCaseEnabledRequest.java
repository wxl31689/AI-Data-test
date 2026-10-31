package com.wxl.testdataagent.testcase;

import jakarta.validation.constraints.NotNull;

public record SetCaseEnabledRequest(
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}

