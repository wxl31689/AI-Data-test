package com.wxl.testdataagent.testrun;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TestRunRepository extends JpaRepository<TestRunEntity, String> {
    List<TestRunEntity> findByProjectIdOrderByStartedAtDesc(String projectId);

    Optional<TestRunEntity> findByRequestKey(String requestKey);
}

