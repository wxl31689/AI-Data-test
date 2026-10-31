package com.wxl.testdataagent.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class LocalApprovalGuard {

    public static final String HEADER = "X-Local-Approval-Token";

    private final String configuredToken;

    public LocalApprovalGuard(@Value("${local-approval.token:}") String configuredToken) {
        this.configuredToken = configuredToken == null ? "" : configuredToken;
    }

    public void requireApproval(HttpServletRequest request, String suppliedToken) {
        if (!isLoopback(request.getRemoteAddr())) {
            throw new ApprovalDeniedException("审批操作仅允许从本机访问");
        }
        if (configuredToken.isBlank()) {
            throw new ApprovalDeniedException("本地审批令牌未配置，审批和 Agent 执行已禁用");
        }
        byte[] expected = configuredToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = (suppliedToken == null ? "" : suppliedToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new ApprovalDeniedException("本地审批令牌无效");
        }
    }

    private boolean isLoopback(String remoteAddress) {
        try {
            return java.util.Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1")
                    .contains(remoteAddress == null ? "" : remoteAddress);
        } catch (Exception exception) {
            return false;
        }
    }
}
