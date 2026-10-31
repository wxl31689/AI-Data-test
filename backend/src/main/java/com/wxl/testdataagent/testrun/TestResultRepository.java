package com.wxl.testdataagent.testrun;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestResultRepository extends JpaRepository<TestResultEntity, String> {

    List<TestResultEntity> findByRunIdOrderByCreatedAtAsc(String runId);
}

