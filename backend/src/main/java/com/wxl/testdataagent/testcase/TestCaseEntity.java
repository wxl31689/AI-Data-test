package com.wxl.testdataagent.testcase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_case")
public class TestCaseEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "project_id", length = 36, nullable = false, updatable = false)
    private String projectId;

    @Column(name = "api_definition_id", length = 36, nullable = false)
    private String apiDefinitionId;

    @Column(length = 180, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_type", length = 24, nullable = false)
    private ScenarioType scenarioType;

    @Column(name = "request_data", columnDefinition = "TEXT", nullable = false)
    private String requestData;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String assertions;

    @Enumerated(EnumType.STRING)
    @Column(length = 8, nullable = false)
    private CasePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private CaseSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 24, nullable = false)
    private ReviewStatus reviewStatus;

    @Column(name = "review_record_id", length = 36)
    private String reviewRecordId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TestCaseEntity() {
    }

    public TestCaseEntity(
            String projectId,
            String apiDefinitionId,
            String name,
            ScenarioType scenarioType,
            String requestData,
            String assertions,
            CasePriority priority,
            CaseSource source,
            ReviewStatus reviewStatus
    ) {
        this.id = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.source = source;
        this.version = 1;
        this.enabled = true;
        update(apiDefinitionId, name, scenarioType, requestData, assertions, priority);
        this.reviewStatus = reviewStatus;
    }

    public void update(
            String apiDefinitionId,
            String name,
            ScenarioType scenarioType,
            String requestData,
            String assertions,
            CasePriority priority
    ) {
        this.apiDefinitionId = apiDefinitionId;
        this.name = name;
        this.scenarioType = scenarioType;
        this.requestData = requestData;
        this.assertions = assertions;
        this.priority = priority;
        this.reviewStatus = ReviewStatus.DRAFT;
        this.reviewRecordId = null;
        if (this.id != null && this.updatedAt != null) {
            this.version++;
        }
    }

    public void review(String reviewRecordId) {
        if (!enabled) {
            throw new IllegalStateException("停用用例不能审核");
        }
        this.reviewStatus = ReviewStatus.REVIEWED;
        this.reviewRecordId = java.util.Objects.requireNonNull(reviewRecordId);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            this.reviewStatus = ReviewStatus.DISABLED;
        } else if (this.reviewStatus == ReviewStatus.DISABLED) {
            this.reviewStatus = ReviewStatus.DRAFT;
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getApiDefinitionId() { return apiDefinitionId; }
    public String getName() { return name; }
    public ScenarioType getScenarioType() { return scenarioType; }
    public String getRequestData() { return requestData; }
    public String getAssertions() { return assertions; }
    public CasePriority getPriority() { return priority; }
    public CaseSource getSource() { return source; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public String getReviewRecordId() { return reviewRecordId; }
    public int getVersion() { return version; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

