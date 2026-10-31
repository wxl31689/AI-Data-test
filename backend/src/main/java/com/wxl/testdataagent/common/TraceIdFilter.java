package com.wxl.testdataagent.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incomingTraceId = request.getHeader(HEADER_NAME);
        String traceId = incomingTraceId == null || incomingTraceId.isBlank()
                ? UUID.randomUUID().toString()
                : incomingTraceId.trim();

        request.setAttribute(RequestTrace.ATTRIBUTE_NAME, traceId);
        response.setHeader(HEADER_NAME, traceId);
        filterChain.doFilter(request, response);
    }
}

