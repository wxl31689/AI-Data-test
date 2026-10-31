package com.wxl.testdataagent.testrun;

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
@Table(
        name = "test_run",
        uniqueConstraints = @UniqueConstraint(name = "uk_test_run_request_key", columnNames = "request_key")
)
public class TestRunEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;

    @Column(name = "run_name", length = 180, nullable = false)
    private String runName;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 24, nullable = false)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private RunStatus status;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "processed_count")
    private Integer processedCount;

    @Column(name = "pass_count", nullable = false)
    private int passCount;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "parent_run_id", length = 36)
    private String parentRunId;

    @Column(name = "request_key", length = 80, unique = true)
    private String requestKey;

    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;

    protected TestRunEntity() {
    }

    public TestRunEntity(
            String projectId,
            String runName,
            TriggerType triggerType,
            int totalCount,
            String parentRunId,
            String requestKey
    ) {
        this.id = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.runName = runName;
        this.triggerType = triggerType;
        this.status = RunStatus.RUNNING;
        this.totalCount = totalCount;
        this.processedCount = 0;
        this.parentRunId = parentRunId;
        this.requestKey = requestKey;
        this.startedAt = Instant.now();
    }

    public void record(ResultStatus resultStatus) {
        this.processedCount = getProcessedCount() + 1;
        switch (resultStatus) {
            case PASS -> this.passCount++;
            case FAIL -> this.failCount++;
            case ERROR -> this.errorCount++;
            case SKIPPED -> this.skippedCount++;
        }
    }

    public void complete() {
        this.status = RunStatus.COMPLETED;
        this.finishedAt = Instant.now();
    }

    public void fail() {
        this.status = RunStatus.FAILED;
        this.finishedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getRunName() { return runName; }
    public TriggerType getTriggerType() { return triggerType; }
    public RunStatus getStatus() { return status; }
    public int getTotalCount() { return totalCount; }
    public int getProcessedCount() { return processedCount == null ? 0 : processedCount; }
    public int getPassCount() { return passCount; }
    public int getFailCount() { return failCount; }
    public int getErrorCount() { return errorCount; }
    public int getSkippedCount() { return skippedCount; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getParentRunId() { return parentRunId; }
    public String getRequestKey() { return requestKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
}

