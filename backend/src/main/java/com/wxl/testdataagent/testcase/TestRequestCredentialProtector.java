package com.wxl.testdataagent.testcase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wxl.testdataagent.common.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TestRequestCredentialProtector {

    private static final Pattern ENV_REFERENCE = Pattern.compile("^\\{\\{env:(TEST_[A-Z0-9_]{1,58})}}$");
    private static final String MASK = "***";

    private final Function<String, String> environment;

    public TestRequestCredentialProtector() {
        this(System::getenv);
    }

    TestRequestCredentialProtector(Function<String, String> environment) {
        this.environment = environment;
    }

    public void validateForStorage(TestRequestConfig request) {
        validateMap(request.pathParams(), "pathParams");
        validateMap(request.queryParams(), "queryParams");
        request.headers().forEach((name, value) -> validateSecret(name, value, "headers." + name));
        validateJson(request.body(), "body");
    }

    public TestRequestConfig maskForResponse(TestRequestConfig request) {
        return new TestRequestConfig(
                maskMap(request.pathParams()),
                maskMap(request.queryParams()),
                maskHeaders(request.headers()),
                maskJson(request.body()),
                request.timeoutMillis()
        );
    }

    public TestRequestConfig resolveForExecution(TestRequestConfig request) {
        return new TestRequestConfig(
                resolveMap(request.pathParams(), "pathParams"),
                resolveMap(request.queryParams(), "queryParams"),
                resolveHeaders(request.headers()),
                resolveJson(request.body(), "body"),
                request.timeoutMillis()
        );
    }

    private void validateMap(Map<String, Object> values, String path) {
        values.forEach((name, value) -> {
            validateSecret(name, value, path + "." + name);
            if (value instanceof Map<?, ?> || value instanceof java.util.Collection<?>) {
                throw new InvalidRequestException(path + " 只允许扁平参数，嵌套数据请使用请求体");
            }
        });
    }

    private void validateJson(JsonNode node, String path) {
        if (node == null) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (isSensitive(entry.getKey())) {
                    validateSecret(entry.getKey(), entry.getValue().isTextual() ? entry.getValue().asText() : null,
                            path + "." + entry.getKey());
                }
                validateJson(entry.getValue(), path + "." + entry.getKey());
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) validateJson(node.get(i), path + "[" + i + "]");
        }
    }

    private void validateSecret(String name, Object value, String path) {
        if (!isSensitive(name)) return;
        if (!(value instanceof String text) || !ENV_REFERENCE.matcher(text).matches()) {
            throw new InvalidRequestException(path + " 是敏感字段，只允许使用 {{env:变量名}} 引用，不能保存明文凭据");
        }
    }

    private Map<String, Object> maskMap(Map<String, Object> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((name, value) -> result.put(name, isSensitive(name) ? safeReferenceOrMask(value) : maskValue(value)));
        return result;
    }

    private Object maskValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key),
                    isSensitive(String.valueOf(key)) ? safeReferenceOrMask(item) : maskValue(item)));
            return copy;
        }
        if (value instanceof java.util.Collection<?> items) return items.stream().map(this::maskValue).toList();
        return value;
    }

    private Map<String, String> maskHeaders(Map<String, String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((name, value) -> result.put(name,
                isSensitive(name) ? String.valueOf(safeReferenceOrMask(value)) : value));
        return result;
    }

    private JsonNode maskJson(JsonNode node) {
        if (node == null) return null;
        JsonNode copy = node.deepCopy();
        maskJsonInPlace(copy);
        return copy;
    }

    private void maskJsonInPlace(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.fields().forEachRemaining(entry -> {
                if (isSensitive(entry.getKey())) {
                    object.put(entry.getKey(), String.valueOf(safeReferenceOrMask(
                            entry.getValue().isTextual() ? entry.getValue().asText() : null)));
                } else {
                    maskJsonInPlace(entry.getValue());
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::maskJsonInPlace);
        }
    }

    private Map<String, Object> resolveMap(Map<String, Object> values, String path) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((name, value) -> result.put(name,
                isSensitive(name) ? resolveReference(value, path + "." + name) : value));
        return result;
    }

    private Map<String, String> resolveHeaders(Map<String, String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((name, value) -> result.put(name,
                isSensitive(name) ? resolveReference(value, "headers." + name) : value));
        return result;
    }

    private JsonNode resolveJson(JsonNode node, String path) {
        if (node == null) return null;
        JsonNode copy = node.deepCopy();
        resolveJsonInPlace(copy, path);
        return copy;
    }

    private void resolveJsonInPlace(JsonNode node, String path) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.fields().forEachRemaining(entry -> {
                if (isSensitive(entry.getKey())) {
                    object.put(entry.getKey(), resolveReference(
                            entry.getValue().isTextual() ? entry.getValue().asText() : null,
                            path + "." + entry.getKey()));
                } else {
                    resolveJsonInPlace(entry.getValue(), path + "." + entry.getKey());
                }
            });
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) resolveJsonInPlace(array.get(i), path + "[" + i + "]");
        }
    }

    private String resolveReference(Object value, String path) {
        if (!(value instanceof String text)) {
            throw new InvalidRequestException(path + " 包含无法安全解析的旧凭据，请改为环境变量引用");
        }
        Matcher matcher = ENV_REFERENCE.matcher(text);
        if (!matcher.matches()) {
            throw new InvalidRequestException(path + " 包含明文或无效凭据引用，已拒绝执行");
        }
        String resolved = environment.apply(matcher.group(1));
        if (resolved == null || resolved.isBlank()) {
            throw new InvalidRequestException(path + " 引用的运行时凭据未配置");
        }
        return resolved;
    }

    private Object safeReferenceOrMask(Object value) {
        return value instanceof String text && ENV_REFERENCE.matcher(text).matches() ? text : MASK;
    }

    private boolean isSensitive(String name) {
        if (name == null) return false;
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "-");
        return normalized.equals("authorization") || normalized.equals("proxy-authorization")
                || normalized.equals("cookie") || normalized.equals("set-cookie")
                || normalized.contains("token") || normalized.contains("password")
                || normalized.contains("passwd") || normalized.contains("secret")
                || normalized.contains("api-key") || normalized.contains("apikey");
    }
}
