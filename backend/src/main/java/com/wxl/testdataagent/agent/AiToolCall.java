package com.wxl.testdataagent.agent;

record AiToolCall(
        String responseId,
        String callId,
        String name,
        String arguments
) {
}
