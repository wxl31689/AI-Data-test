package com.wxl.demoapi.common;

import java.time.Instant;

public record DemoErrorResponse(
        String code,
        String message,
        Instant timestamp
) {
}

