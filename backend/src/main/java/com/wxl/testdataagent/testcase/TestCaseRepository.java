package com.wxl.testdataagent.testcase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCaseEntity, String> {

    List<TestCaseEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);
}

