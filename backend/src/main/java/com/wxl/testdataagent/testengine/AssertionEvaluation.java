package com.wxl.testdataagent.testengine;

public record AssertionEvaluation(
        String type,
        String expected,
        String actual,
        boolean passed,
        String message
) {
}

