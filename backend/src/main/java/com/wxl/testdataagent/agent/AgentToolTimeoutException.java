package com.wxl.testdataagent.agent;

public class AgentToolTimeoutException extends RuntimeException {
    private final String approvalId;
    private final String agentTaskId;
    private final String auditId;
    public AgentToolTimeoutException(String approvalId, String agentTaskId, String auditId) {
        super("工具等待超时，任务创建结果待确认；请使用审批标识查询，勿重复授权");
        this.approvalId = approvalId;
        this.agentTaskId = agentTaskId;
        this.auditId = auditId;
    }
    public String getApprovalId() { return approvalId; }
    public String getAgentTaskId() { return agentTaskId; }
    public String getAuditId() { return auditId; }
}
