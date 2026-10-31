package com.wxl.testdataagent.dataquality;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataQualityIssueRepository extends JpaRepository<DataQualityIssueEntity, String> {
    List<DataQualityIssueEntity> findByTaskIdOrderByRowNumberAscColumnNameAsc(String taskId);

    void deleteByTaskId(String taskId);
}
