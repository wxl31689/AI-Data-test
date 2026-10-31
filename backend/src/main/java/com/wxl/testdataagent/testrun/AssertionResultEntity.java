package com.wxl.testdataagent.testrun;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assertion_result")
public class AssertionResultEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "test_result_id", length = 36, nullable = false)
    private String testResultId;

    @Column(name = "assertion_type", length = 40, nullable = false)
    private String assertionType;

    @Column(name = "expected_value", columnDefinition = "TEXT")
    private String expectedValue;

    @Column(name = "actual_value", columnDefinition = "TEXT")
    private String actualValue;

    @Column(nullable = false)
    private boolean passed;

    @Column(length = 1000, nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AssertionResultEntity() {
    }

    public AssertionResultEntity(
            String testResultId,
            String assertionType,
            String expectedValue,
            String actualValue,
            boolean passed,
            String message
    ) {
        this.id = UUID.randomUUID().toString();
        this.testResultId = testResultId;
        this.assertionType = assertionType;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.passed = passed;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTestResultId() { return testResultId; }
    public String getAssertionType() { return assertionType; }
    public String getExpectedValue() { return expectedValue; }
    public String getActualValue() { return actualValue; }
    public boolean isPassed() { return passed; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}

