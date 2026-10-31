package com.wxl.testdataagent.testrun;

import jakarta.validation.constraints.Size;

public record RerunFailedRequest(
        @Size(max = 80, message = "幂等键不能超过 80 个字符")
        String clientRequestId
) {
}
