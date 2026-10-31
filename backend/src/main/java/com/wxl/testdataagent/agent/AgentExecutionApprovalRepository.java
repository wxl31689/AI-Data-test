package com.wxl.testdataagent.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface AgentExecutionApprovalRepository extends JpaRepository<AgentExecutionApprovalEntity, String> {
    Optional<AgentExecutionApprovalEntity> findByRequestKey(String requestKey);
}
