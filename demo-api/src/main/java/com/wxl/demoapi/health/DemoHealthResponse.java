package com.wxl.demoapi.health;

import java.time.Instant;

public record DemoHealthResponse(
        String status,
        String service,
        Instant checkedAt
) {
}

