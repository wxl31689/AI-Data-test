package com.wxl.testdataagent.common;

import com.wxl.testdataagent.agent.AgentUnavailableException;
import com.wxl.testdataagent.agent.AiProviderException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(com.wxl.testdataagent.agent.AgentToolTimeoutException.class)
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> handleToolTimeout(
            com.wxl.testdataagent.agent.AgentToolTimeoutException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(new ApiResponse<>(
                false, "AGENT_TOOL_TIMEOUT", exception.getMessage(), java.util.Map.of(
                "approvalId", exception.getApprovalId(), "agentTaskId", exception.getAgentTaskId(),
                "auditId", exception.getAuditId()), RequestTrace.get(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest().body(ApiResponse.error(
                "VALIDATION_ERROR",
                message,
                RequestTrace.get(request)
        ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                "RESOURCE_NOT_FOUND",
                exception.getMessage(),
                RequestTrace.get(request)
        ));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(
            InvalidRequestException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                "INVALID_REQUEST",
                exception.getMessage(),
                RequestTrace.get(request)
        ));
    }

    @ExceptionHandler(ApprovalDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleApprovalDenied(
            ApprovalDeniedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                "APPROVAL_DENIED",
                exception.getMessage(),
                RequestTrace.get(request)
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.error(
                "FILE_TOO_LARGE",
                "上传文件超过允许大小",
                RequestTrace.get(request)
        ));
    }

    @ExceptionHandler(AgentUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleAgentUnavailable(
            AgentUnavailableException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(
                "AGENT_UNAVAILABLE",
                exception.getMessage(),
                RequestTrace.get(request)
        ));
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiProvider(
            AiProviderException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(
                "AI_PROVIDER_ERROR",
                exception.getMessage(),
                RequestTrace.get(request)
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                "INTERNAL_ERROR",
                "服务暂时不可用，请使用 traceId 联系开发人员定位问题",
                RequestTrace.get(request)
        ));
    }
}

