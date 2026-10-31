package com.wxl.testdataagent.dataquality;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataQualityTaskRepository extends JpaRepository<DataQualityTaskEntity, String> {
    List<DataQualityTaskEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
