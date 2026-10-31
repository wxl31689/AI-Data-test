package com.wxl.testdataagent.project;

import java.time.Instant;

public record ProjectResponse(
        String id,
        String name,
        String description,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(ProjectEntity entity) {
        return new ProjectResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

