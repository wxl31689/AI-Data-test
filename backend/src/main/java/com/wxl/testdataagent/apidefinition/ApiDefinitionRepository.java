package com.wxl.testdataagent.apidefinition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiDefinitionRepository extends JpaRepository<ApiDefinitionEntity, String> {

    List<ApiDefinitionEntity> findByProjectIdOrderByPathAscMethodAsc(String projectId);

    Optional<ApiDefinitionEntity> findByProjectIdAndMethodAndPath(
            String projectId,
            String method,
            String path
    );
}

