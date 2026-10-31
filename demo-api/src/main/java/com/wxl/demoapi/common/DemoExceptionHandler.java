package com.wxl.demoapi.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class DemoExceptionHandler {

    @ExceptionHandler(DemoApiException.class)
    public ResponseEntity<DemoErrorResponse> handleDemoApiException(DemoApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new DemoErrorResponse(
                exception.getCode(),
                exception.getMessage(),
                Instant.now()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<DemoErrorResponse> handleBodyValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new DemoErrorResponse(
                "VALIDATION_ERROR",
                message,
                Instant.now()
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<DemoErrorResponse> handleParameterValidation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new DemoErrorResponse(
                "VALIDATION_ERROR",
                message,
                Instant.now()
        ));
    }
}

