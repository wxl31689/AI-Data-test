package com.wxl.testdataagent.testcase;

import jakarta.validation.constraints.AssertTrue;

public record ReviewTestCaseRequest(
        @AssertTrue(message = "必须明确确认审核通过")
        boolean approved
) {
}
