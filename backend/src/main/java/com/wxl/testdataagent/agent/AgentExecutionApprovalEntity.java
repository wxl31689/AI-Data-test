package com.wxl.testdataagent.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_execution_approval", uniqueConstraints =
        @UniqueConstraint(name = "uk_agent_approval_request_key", columnNames = "request_key"))
class AgentExecutionApprovalEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "agent_task_id", length = 36, nullable = false, updatable = false)
    private String agentTaskId;

    @Column(name = "project_id", length = 36, nullable = false, updatable = false)
    private String projectId;

    @Column(name = "case_ids_json", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String caseIdsJson;

    @Column(name = "case_versions_json", columnDefinition = "TEXT")
    private String caseVersionsJson;

    @Column(name = "review_ids_json", columnDefinition = "TEXT")
    private String reviewIdsJson;

    @Column(name = "run_name", length = 180, nullable = false, updatable = false)
    private String runName;

    @Column(name = "request_key", length = 80, nullable = false, updatable = false, unique = true)
    private String requestKey;

    @Column(name = "approved_by", length = 80, nullable = false, updatable = false)
    private String approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private AgentExecutionApprovalStatus status;

    @Column(name = "run_id", length = 36)
    private String runId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected AgentExecutionApprovalEntity() {
    }

    AgentExecutionApprovalEntity(
            String projectId,
            String caseIdsJson,
            String runName,
            String requestKey,
            Instant expiresAt
    ) {
        this.id = UUID.randomUUID().toString();
        this.agentTaskId = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.caseIdsJson = caseIdsJson;
        this.runName = runName;
        this.requestKey = requestKey;
        this.approvedBy = "LOCAL_DEMO_APPROVER";
        this.status = AgentExecutionApprovalStatus.APPROVED;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    void markExecuted(String runId) {
        this.status = AgentExecutionApprovalStatus.EXECUTED;
        this.runId = runId;
        this.usedAt = Instant.now();
    }

    void markFailed() {
        this.status = AgentExecutionApprovalStatus.FAILED;
        this.usedAt = Instant.now();
    }

    void bindCaseVersions(String caseVersionsJson) { this.caseVersionsJson = caseVersionsJson; }
    void bindReviews(String reviewIdsJson) { this.reviewIdsJson = reviewIdsJson; }

    void markTimedOut() { this.status = AgentExecutionApprovalStatus.TIMED_OUT; }

    String getId() { return id; }
    String getAgentTaskId() { return agentTaskId; }
    String getProjectId() { return projectId; }
    String getCaseIdsJson() { return caseIdsJson; }
    String getCaseVersionsJson() { return caseVersionsJson; }
    String getReviewIdsJson() { return reviewIdsJson; }
    String getRunName() { return runName; }
    String getRequestKey() { return requestKey; }
    String getApprovedBy() { return approvedBy; }
    AgentExecutionApprovalStatus getStatus() { return status; }
    String getRunId() { return runId; }
    Instant getCreatedAt() { return createdAt; }
    Instant getExpiresAt() { return expiresAt; }
    Instant getUsedAt() { return usedAt; }
}
