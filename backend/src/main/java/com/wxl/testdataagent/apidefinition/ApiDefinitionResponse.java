package com.wxl.testdataagent.apidefinition;

import java.time.Instant;

public record ApiDefinitionResponse(
        String id,
        String projectId,
        String name,
        String path,
        String method,
        String summary,
        String baseUrl,
        String parametersJson,
        String requestSchema,
        String responseSchema,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static ApiDefinitionResponse from(ApiDefinitionEntity entity) {
        return new ApiDefinitionResponse(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getPath(),
                entity.getMethod(),
                entity.getSummary(),
                entity.getBaseUrl(),
                entity.getParametersJson(),
                entity.getRequestSchema(),
                entity.getResponseSchema(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

