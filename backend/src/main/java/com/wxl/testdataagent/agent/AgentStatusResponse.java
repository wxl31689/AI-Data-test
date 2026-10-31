package com.wxl.testdataagent.agent;

import java.util.List;

public record AgentStatusResponse(
        boolean enabled,
        boolean configured,
        String provider,
        String model,
        List<String> capabilities,
        String verdictPolicy
) {
}
