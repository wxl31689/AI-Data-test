package com.wxl.testdataagent.apidefinition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "api_definition",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_api_project_method_path",
                columnNames = {"project_id", "method", "path"}
        )
)
public class ApiDefinitionEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "project_id", length = 36, nullable = false, updatable = false)
    private String projectId;

    @Column(length = 160, nullable = false)
    private String name;

    @Column(length = 500, nullable = false)
    private String path;

    @Column(length = 16, nullable = false)
    private String method;

    @Column(length = 500)
    private String summary;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "request_schema", columnDefinition = "TEXT")
    private String requestSchema;

    @Column(name = "response_schema", columnDefinition = "TEXT")
    private String responseSchema;

    @Column(name = "auth_config", columnDefinition = "TEXT")
    private String authConfig;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApiDefinitionEntity() {
    }

    public ApiDefinitionEntity(
            String projectId,
            String name,
            String path,
            String method,
            String summary,
            String baseUrl,
            String parametersJson,
            String requestSchema,
            String responseSchema
    ) {
        this.id = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.enabled = true;
        update(name, summary, baseUrl, parametersJson, requestSchema, responseSchema);
        this.path = path;
        this.method = method;
    }

    public void update(
            String name,
            String summary,
            String baseUrl,
            String parametersJson,
            String requestSchema,
            String responseSchema
    ) {
        this.name = name;
        this.summary = summary;
        this.baseUrl = baseUrl;
        this.parametersJson = parametersJson;
        this.requestSchema = requestSchema;
        this.responseSchema = responseSchema;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public String getSummary() {
        return summary;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public String getRequestSchema() {
        return requestSchema;
    }

    public String getResponseSchema() {
        return responseSchema;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

