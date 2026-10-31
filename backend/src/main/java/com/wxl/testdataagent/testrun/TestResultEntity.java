package com.wxl.testdataagent.testrun;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_result")
public class TestResultEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "run_id", length = 36, nullable = false)
    private String runId;

    @Column(name = "test_case_id", length = 36, nullable = false)
    private String testCaseId;

    @Column(name = "case_name", length = 180, nullable = false)
    private String caseName;

    @Column(name = "case_snapshot", columnDefinition = "TEXT", nullable = false)
    private String caseSnapshot;

    @Column(name = "request_summary", columnDefinition = "TEXT")
    private String requestSummary;

    @Column(name = "response_summary", columnDefinition = "TEXT")
    private String responseSummary;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", length = 24, nullable = false)
    private ResultStatus resultStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TestResultEntity() {
    }

    public TestResultEntity(
            String runId,
            String testCaseId,
            String caseName,
            String caseSnapshot,
            String requestSummary,
            String responseSummary,
            Integer statusCode,
            Long durationMs,
            ResultStatus resultStatus,
            String errorMessage
    ) {
        this.id = UUID.randomUUID().toString();
        this.runId = runId;
        this.testCaseId = testCaseId;
        this.caseName = caseName;
        this.caseSnapshot = caseSnapshot;
        this.requestSummary = requestSummary;
        this.responseSummary = responseSummary;
        this.statusCode = statusCode;
        this.durationMs = durationMs;
        this.resultStatus = resultStatus;
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public String getTestCaseId() { return testCaseId; }
    public String getCaseName() { return caseName; }
    public String getCaseSnapshot() { return caseSnapshot; }
    public String getRequestSummary() { return requestSummary; }
    public String getResponseSummary() { return responseSummary; }
    public Integer getStatusCode() { return statusCode; }
    public Long getDurationMs() { return durationMs; }
    public ResultStatus getResultStatus() { return resultStatus; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}

