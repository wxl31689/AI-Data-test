package com.wxl.testdataagent.testengine;

import com.wxl.testdataagent.common.InvalidRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RegisteredTargetPolicy {

    private final Set<String> allowedBaseUrls;

    public RegisteredTargetPolicy(@Value("${test-engine.allowed-base-urls}") String configuredUrls) {
        this.allowedBaseUrls = Arrays.stream(configuredUrls.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String validate(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new InvalidRequestException("接口定义没有配置基础地址");
        }
        if (path == null || !path.startsWith("/") || path.startsWith("//")) {
            throw new InvalidRequestException("接口路径格式不安全");
        }
        String normalized = normalize(baseUrl);
        if (!allowedBaseUrls.contains(normalized)) {
            throw new InvalidRequestException("目标地址未登记到执行白名单");
        }
        return normalized;
    }

    private String normalize(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

