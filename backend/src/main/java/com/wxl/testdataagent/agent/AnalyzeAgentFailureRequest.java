package com.wxl.testdataagent.agent;

import jakarta.validation.constraints.Size;

public record AnalyzeAgentFailureRequest(
        @Size(max = 500, message = "分析重点不能超过 500 个字符")
        String focus
) {
}
