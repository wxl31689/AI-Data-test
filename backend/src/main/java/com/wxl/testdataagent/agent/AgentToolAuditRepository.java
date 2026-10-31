package com.wxl.testdataagent.agent;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface AgentToolAuditRepository extends JpaRepository<AgentToolAuditEntity, String> {
    List<AgentToolAuditEntity> findByProjectIdOrderByStartedAtDesc(String projectId, Pageable pageable);
    List<AgentToolAuditEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
    java.util.Optional<AgentToolAuditEntity> findFirstByApprovalIdOrderByStartedAtDesc(String approvalId);
}
