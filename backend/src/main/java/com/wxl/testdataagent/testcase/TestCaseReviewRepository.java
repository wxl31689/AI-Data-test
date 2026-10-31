package com.wxl.testdataagent.testcase;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TestCaseReviewRepository extends JpaRepository<TestCaseReviewEntity, String> {
    List<TestCaseReviewEntity> findByTestCaseIdAndCaseVersionOrderByReviewedAtDesc(String caseId, int version);
}
