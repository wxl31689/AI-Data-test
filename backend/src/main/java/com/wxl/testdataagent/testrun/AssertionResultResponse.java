package com.wxl.testdataagent.testrun;

import java.time.Instant;

public record AssertionResultResponse(
        String id,
        String assertionType,
        String expectedValue,
        String actualValue,
        boolean passed,
        String message,
        Instant createdAt
) {
    static AssertionResultResponse from(AssertionResultEntity entity) {
        return new AssertionResultResponse(
                entity.getId(),
                entity.getAssertionType(),
                entity.getExpectedValue(),
                entity.getActualValue(),
                entity.isPassed(),
                entity.getMessage(),
                entity.getCreatedAt()
        );
    }
}

