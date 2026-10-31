package com.wxl.testdataagent.testrun;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssertionResultRepository extends JpaRepository<AssertionResultEntity, String> {

    List<AssertionResultEntity> findByTestResultIdOrderByCreatedAtAsc(String testResultId);
}

