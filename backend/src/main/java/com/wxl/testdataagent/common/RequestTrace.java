package com.wxl.testdataagent.common;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestTrace {

    public static final String ATTRIBUTE_NAME = "traceId";

    private RequestTrace() {
    }

    public static String get(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE_NAME);
        return value == null ? "unknown" : value.toString();
    }
}

