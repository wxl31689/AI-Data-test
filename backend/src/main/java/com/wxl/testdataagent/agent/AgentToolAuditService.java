package com.wxl.testdataagent.agent;

import com.wxl.testdataagent.testengine.EvidenceSanitizer;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentToolAuditService {

    private static final int MAX_SUMMARY_CHARS = 4_000;

    private final AgentToolAuditRepository repository;
    private final EvidenceSanitizer sanitizer;

    public AgentToolAuditService(AgentToolAuditRepository repository, EvidenceSanitizer sanitizer) {
        this.repository = repository;
        this.sanitizer = sanitizer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String start(
            String agentTaskId,
            String toolName,
            String projectId,
            String runId,
            String approvalId,
            String inputSummary
    ) {
        String safeInput = sanitizer.sanitizeResponse(inputSummary, MAX_SUMMARY_CHARS);
        return repository.save(new AgentToolAuditEntity(
                agentTaskId, toolName, projectId, runId, approvalId, safeInput == null ? "" : safeInput
        )).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(String id, String runId, String summary) {
        finish(id, AgentToolAuditStatus.SUCCESS, runId, summary, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void linkRun(String id, String runId) {
        AgentToolAuditEntity entity = repository.findById(id).orElseThrow();
        entity.linkRun(runId);
        repository.save(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(String id, AgentToolAuditStatus status, String errorType, String summary) {
        finish(id, status, null, summary, errorType);
    }

    @Transactional(readOnly = true)
    public List<AgentToolAuditResponse> list(String projectId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        List<AgentToolAuditEntity> records = projectId == null || projectId.isBlank()
                ? repository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit))
                : repository.findByProjectIdOrderByStartedAtDesc(projectId, PageRequest.of(0, limit));
        return records.stream().map(this::toResponse).toList();
    }

    private void finish(
            String id,
            AgentToolAuditStatus status,
            String runId,
            String summary,
            String errorType
    ) {
        AgentToolAuditEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Agent 工具审计记录不存在"));
        String safeSummary = sanitizer.sanitizeResponse(summary, MAX_SUMMARY_CHARS);
        entity.finish(status, runId, safeSummary, errorType == null ? null : bound(errorType, 120));
        repository.save(entity);
    }

    private AgentToolAuditResponse toResponse(AgentToolAuditEntity entity) {
        return new AgentToolAuditResponse(
                entity.getId(), entity.getAgentTaskId(), entity.getToolName(), entity.getProjectId(),
                entity.getRunId(), entity.getApprovalId(), entity.getInputSummary(), entity.getStartedAt(),
                entity.getFinishedAt(), entity.getDurationMs(), entity.getStatus(), entity.getResultSummary(),
                entity.getErrorType()
        );
    }

    private String bound(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
