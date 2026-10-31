package com.wxl.testdataagent.testengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class EvidenceSanitizer {

    private static final Pattern SENSITIVE_PAIR = Pattern.compile(
            "(?i)(authorization|token|password|secret|cookie|api[-_]?key|apikey)(\\s*[:=]\\s*)([^\\s,;]+)"
    );
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._-]+");
    private static final Pattern OPENAI_STYLE_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_-]{8,}\\b");

    private final ObjectMapper objectMapper;

    public EvidenceSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        return headers.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> isSensitive(entry.getKey()) ? "***" : entry.getValue()
        ));
    }

    public JsonNode sanitizeJson(JsonNode source) {
        if (source == null) {
            return null;
        }
        JsonNode copy = source.deepCopy();
        sanitizeNode(copy);
        return copy;
    }

    public String sanitizeResponse(String responseBody, int maxChars) {
        if (responseBody == null) {
            return null;
        }
        String sanitized = responseBody;
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            sanitized = objectMapper.writeValueAsString(sanitizeJson(node));
        } catch (Exception ignored) {
            // Non-JSON responses are retained as bounded plain text evidence.
            sanitized = redactPlainText(sanitized);
        }
        return sanitized.length() <= maxChars ? sanitized : sanitized.substring(0, maxChars) + "…";
    }

    private String redactPlainText(String value) {
        String redacted = SENSITIVE_PAIR.matcher(value).replaceAll("$1$2***");
        redacted = BEARER_TOKEN.matcher(redacted).replaceAll("Bearer ***");
        return OPENAI_STYLE_KEY.matcher(redacted).replaceAll("sk-***");
    }

    private void sanitizeNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitive(field.getKey())) {
                    objectNode.put(field.getKey(), "***");
                } else {
                    sanitizeNode(field.getValue());
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::sanitizeNode);
        }
    }

    public boolean isSensitive(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase(Locale.ROOT).replace('_', '-');
        return normalized.contains("authorization")
                || normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("cookie")
                || normalized.contains("api-key")
                || normalized.contains("apikey");
    }
}

