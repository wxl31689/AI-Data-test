package com.wxl.testdataagent.testcase;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_case_review")
public class TestCaseReviewEntity {
    @Id @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @Column(name = "test_case_id", length = 36, nullable = false, updatable = false)
    private String testCaseId;
    @Column(name = "case_version", nullable = false, updatable = false)
    private int caseVersion;
    @Column(length = 80, nullable = false, updatable = false)
    private String reviewer;
    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    protected TestCaseReviewEntity() { }

    public TestCaseReviewEntity(TestCaseEntity testCase) {
        id = UUID.randomUUID().toString();
        testCaseId = testCase.getId();
        caseVersion = testCase.getVersion();
        reviewer = "LOCAL_DEMO_APPROVER";
        reviewedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTestCaseId() { return testCaseId; }
    public int getCaseVersion() { return caseVersion; }
    public String getReviewer() { return reviewer; }
    public Instant getReviewedAt() { return reviewedAt; }
}
