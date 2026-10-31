package com.wxl.testdataagent.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_tool_audit")
class AgentToolAuditEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "agent_task_id", length = 36, nullable = false, updatable = false)
    private String agentTaskId;

    @Column(name = "tool_name", length = 80, nullable = false, updatable = false)
    private String toolName;

    @Column(name = "project_id", length = 36)
    private String projectId;

    @Column(name = "run_id", length = 36)
    private String runId;

    @Column(name = "approval_id", length = 36)
    private String approvalId;

    @Column(name = "input_summary", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String inputSummary;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private AgentToolAuditStatus status;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "error_type", length = 120)
    private String errorType;

    protected AgentToolAuditEntity() {
    }

    AgentToolAuditEntity(
            String agentTaskId,
            String toolName,
            String projectId,
            String runId,
            String approvalId,
            String inputSummary
    ) {
        this.id = UUID.randomUUID().toString();
        this.agentTaskId = agentTaskId;
        this.toolName = toolName;
        this.projectId = projectId;
        this.runId = runId;
        this.approvalId = approvalId;
        this.inputSummary = inputSummary;
        this.startedAt = Instant.now();
        this.status = AgentToolAuditStatus.STARTED;
    }

    void finish(AgentToolAuditStatus status, String runId, String resultSummary, String errorType) {
        this.status = status;
        if (runId != null) this.runId = runId;
        this.resultSummary = resultSummary;
        this.errorType = errorType;
        this.finishedAt = Instant.now();
        this.durationMs = Math.max(0, Duration.between(startedAt, finishedAt).toMillis());
    }

    void linkRun(String runId) { this.runId = runId; }

    String getId() { return id; }
    String getAgentTaskId() { return agentTaskId; }
    String getToolName() { return toolName; }
    String getProjectId() { return projectId; }
    String getRunId() { return runId; }
    String getApprovalId() { return approvalId; }
    String getInputSummary() { return inputSummary; }
    Instant getStartedAt() { return startedAt; }
    Instant getFinishedAt() { return finishedAt; }
    Long getDurationMs() { return durationMs; }
    AgentToolAuditStatus getStatus() { return status; }
    String getResultSummary() { return resultSummary; }
    String getErrorType() { return errorType; }
}
