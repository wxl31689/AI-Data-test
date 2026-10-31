package com.wxl.testdataagent.common;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        String traceId
) {
    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, "OK", "操作成功", data, traceId);
    }

    public static <T> ApiResponse<T> created(T data, String traceId) {
        return new ApiResponse<>(true, "CREATED", "创建成功", data, traceId);
    }

    public static <T> ApiResponse<T> accepted(T data, String traceId) {
        return new ApiResponse<>(true, "ACCEPTED", "任务已接收", data, traceId);
    }

    public static ApiResponse<Void> error(String code, String message, String traceId) {
        return new ApiResponse<>(false, code, message, null, traceId);
    }
}

