package com.wxl.demoapi.common;

import org.springframework.http.HttpStatus;

public class DemoApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public DemoApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}

