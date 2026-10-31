package com.wxl.testdataagent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wxl.testdataagent.common.InvalidRequestException;
import com.wxl.testdataagent.testcase.AssertionConfig;
import com.wxl.testdataagent.testcase.AssertionType;
import com.wxl.testdataagent.testcase.CasePriority;
import com.wxl.testdataagent.testcase.JsonValueType;
import com.wxl.testdataagent.testcase.ScenarioType;
import com.wxl.testdataagent.testcase.TestRequestConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
class AgentCandidateValidator {

    private static final int MAX_ASSERTIONS = 10;
    private static final int MAX_MAP_ENTRIES = 20;
    private static final Pattern PARAMETER_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,100}");

    private final ObjectMapper objectMapper;

    AgentCandidateValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<AgentTestCaseCandidate> parseCandidates(JsonNode output, int requestedCount) {
        JsonNode candidatesNode = output.path("candidates");
        if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
            throw new AiProviderException("模型没有生成候选用例");
        }
        if (candidatesNode.size() > requestedCount || candidatesNode.size() > 5) {
            throw new AiProviderException("模型生成的候选用例数量超出限制");
        }
        return java.util.stream.StreamSupport.stream(candidatesNode.spliterator(), false)
                .map(this::parseCandidate)
                .toList();
    }

    AgentTestCaseCandidate validateNormalized(AgentTestCaseCandidate candidate) {
        if (candidate == null) throw new InvalidRequestException("候选用例不能为空");
        String name = boundedText(candidate.name(), "用例名称", 180, false);
        String rationale = boundedText(candidate.rationale(), "生成理由", 1000, false);
        String risk = boundedText(candidate.risk(), "风险说明", 1000, true);
        if (candidate.scenarioType() == null || candidate.priority() == null || candidate.request() == null) {
            throw new InvalidRequestException("候选用例的场景、优先级和请求不能为空");
        }
        validateRequest(candidate.request());
        validateAssertions(candidate.assertions());
        return new AgentTestCaseCandidate(
                name,
                candidate.scenarioType(),
                candidate.priority(),
                candidate.request(),
                List.copyOf(candidate.assertions()),
                rationale,
                risk
        );
    }

    private AgentTestCaseCandidate parseCandidate(JsonNode node) {
        try {
            String name = boundedText(node.path("name").asText(), "用例名称", 180, false);
            ScenarioType scenario = ScenarioType.valueOf(node.path("scenarioType").asText());
            CasePriority priority = CasePriority.valueOf(node.path("priority").asText());
            JsonNode requestNode = node.path("request");
            Map<String, Object> pathParams = readObjectMap(requestNode.path("pathParamsJson").asText(), "pathParams");
            Map<String, Object> queryParams = readObjectMap(requestNode.path("queryParamsJson").asText(), "queryParams");
            Map<String, String> headers = readHeaderMap(requestNode.path("headersJson").asText());
            JsonNode body = readJson(requestNode.path("bodyJson").asText(), "bodyJson");
            int timeoutMillis = requestNode.path("timeoutMillis").asInt();
            TestRequestConfig request = new TestRequestConfig(
                    pathParams,
                    queryParams,
                    headers,
                    body,
                    timeoutMillis
            );
            List<AssertionConfig> assertions = java.util.stream.StreamSupport
                    .stream(node.path("assertions").spliterator(), false)
                    .map(this::parseAssertion)
                    .toList();
            AgentTestCaseCandidate candidate = new AgentTestCaseCandidate(
                    name,
                    scenario,
                    priority,
                    request,
                    assertions,
                    boundedText(node.path("rationale").asText(), "生成理由", 1000, false),
                    boundedText(node.path("risk").asText(), "风险说明", 1000, true)
            );
            return validateNormalized(candidate);
        } catch (InvalidRequestException exception) {
            throw new AiProviderException("模型候选用例未通过安全校验: " + exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new AiProviderException("模型生成了不支持的枚举值", exception);
        }
    }

    private AssertionConfig parseAssertion(JsonNode node) {
        try {
            AssertionType type = AssertionType.valueOf(node.path("type").asText());
            String path = blankToNull(node.path("path").asText());
            JsonNode expected = readJson(node.path("expectedJson").asText(), "expectedJson");
            String expectedTypeValue = node.path("expectedType").asText();
            JsonValueType expectedType = "NONE".equals(expectedTypeValue)
                    ? null
                    : JsonValueType.valueOf(expectedTypeValue);
            BigDecimal min = decimalOrNull(node.path("min").asText(), "min");
            BigDecimal max = decimalOrNull(node.path("max").asText(), "max");
            long maxMillisValue = node.path("maxMillis").asLong();
            Long maxMillis = maxMillisValue <= 0 ? null : maxMillisValue;
            return new AssertionConfig(type, path, expected, expectedType, min, max, maxMillis);
        } catch (IllegalArgumentException exception) {
            throw new AiProviderException("模型生成了无效断言", exception);
        }
    }

    private void validateRequest(TestRequestConfig request) {
        validateMap(request.pathParams(), "pathParams");
        validateMap(request.queryParams(), "queryParams");
        if (request.headers().size() > MAX_MAP_ENTRIES) {
            throw new InvalidRequestException("Agent 请求头数量超过限制");
        }
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            validateName(header.getKey(), "请求头");
            if (isSensitiveHeader(header.getKey())) {
                throw new InvalidRequestException("Agent 不允许生成敏感认证请求头: " + header.getKey());
            }
            if (header.getValue() != null && header.getValue().length() > 500) {
                throw new InvalidRequestException("Agent 请求头值过长");
            }
        }
        if (request.timeoutMillis() < 100 || request.timeoutMillis() > 10_000) {
            throw new InvalidRequestException("Agent 生成的超时时间必须在 100–10000 毫秒之间");
        }
        if (request.body() != null && request.body().toString().length() > 10_000) {
            throw new InvalidRequestException("Agent 生成的请求体超过大小限制");
        }
    }

    private void validateAssertions(List<AssertionConfig> assertions) {
        if (assertions == null || assertions.isEmpty() || assertions.size() > MAX_ASSERTIONS) {
            throw new InvalidRequestException("Agent 候选用例必须包含 1–10 条断言");
        }
        for (AssertionConfig assertion : assertions) {
            if (assertion.type() == null) throw new InvalidRequestException("Agent 断言类型不能为空");
            if (assertion.path() != null && assertion.path().length() > 300) {
                throw new InvalidRequestException("Agent JSONPath 过长");
            }
            if (assertion.type() == AssertionType.STATUS_CODE
                    && (assertion.expected() == null || !assertion.expected().canConvertToInt()
                    || assertion.expected().asInt() < 100 || assertion.expected().asInt() > 599)) {
                throw new InvalidRequestException("Agent 状态码断言无效");
            }
            boolean jsonPathRequired = assertion.type() == AssertionType.JSON_PATH_EXISTS
                    || assertion.type() == AssertionType.JSON_VALUE_EQUALS
                    || assertion.type() == AssertionType.JSON_TYPE
                    || assertion.type() == AssertionType.NUMBER_RANGE;
            if (jsonPathRequired && (assertion.path() == null || !assertion.path().startsWith("$"))) {
                throw new InvalidRequestException("Agent JSON 断言必须使用以 $ 开头的 JSONPath");
            }
            if (assertion.type() == AssertionType.JSON_VALUE_EQUALS && assertion.expected() == null) {
                throw new InvalidRequestException("Agent JSON 值断言缺少 expected");
            }
            if (assertion.type() == AssertionType.JSON_TYPE && assertion.expectedType() == null) {
                throw new InvalidRequestException("Agent JSON 类型断言缺少 expectedType");
            }
            if (assertion.type() == AssertionType.NUMBER_RANGE
                    && assertion.min() == null && assertion.max() == null) {
                throw new InvalidRequestException("Agent 数值范围断言缺少边界");
            }
            if (assertion.min() != null && assertion.max() != null
                    && assertion.min().compareTo(assertion.max()) > 0) {
                throw new InvalidRequestException("Agent 数值范围断言最小值大于最大值");
            }
            if (assertion.type() == AssertionType.RESPONSE_TIME
                    && (assertion.maxMillis() == null || assertion.maxMillis() <= 0
                    || assertion.maxMillis() > 30_000)) {
                throw new InvalidRequestException("Agent 响应时间断言阈值无效");
            }
        }
    }

    private Map<String, Object> readObjectMap(String json, String fieldName) {
        JsonNode node = readJson(json, fieldName);
        if (!node.isObject()) throw new AiProviderException("模型字段 " + fieldName + " 必须是 JSON 对象");
        try {
            return objectMapper.convertValue(node, new TypeReference<>() {});
        } catch (IllegalArgumentException exception) {
            throw new AiProviderException("模型字段 " + fieldName + " 无法转换", exception);
        }
    }

    private Map<String, String> readHeaderMap(String json) {
        JsonNode node = readJson(json, "headersJson");
        if (!node.isObject()) throw new AiProviderException("模型字段 headersJson 必须是 JSON 对象");
        node.properties().forEach(entry -> {
            if (!entry.getValue().isTextual()) {
                throw new AiProviderException("模型请求头值必须是字符串");
            }
        });
        try {
            return objectMapper.convertValue(node, new TypeReference<>() {});
        } catch (IllegalArgumentException exception) {
            throw new AiProviderException("模型字段 headersJson 无法转换", exception);
        }
    }

    private JsonNode readJson(String json, String fieldName) {
        if (json == null || json.length() > 10_000) {
            throw new AiProviderException("模型字段 " + fieldName + " 超过大小限制");
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null) throw new AiProviderException("模型字段 " + fieldName + " 不是有效 JSON");
            return node;
        } catch (JsonProcessingException exception) {
            throw new AiProviderException("模型字段 " + fieldName + " 不是有效 JSON", exception);
        }
    }

    private BigDecimal decimalOrNull(String value, String fieldName) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new AiProviderException("模型字段 " + fieldName + " 不是有效数字", exception);
        }
    }

    private void validateMap(Map<String, ?> values, String fieldName) {
        if (values.size() > MAX_MAP_ENTRIES) {
            throw new InvalidRequestException("Agent " + fieldName + " 参数数量超过限制");
        }
        values.forEach((key, value) -> {
            validateName(key, fieldName);
            if (value != null && value.toString().length() > 500) {
                throw new InvalidRequestException("Agent " + fieldName + " 参数值过长");
            }
        });
    }

    private void validateName(String name, String label) {
        if (name == null || !PARAMETER_NAME.matcher(name).matches()) {
            throw new InvalidRequestException("Agent " + label + " 名称无效");
        }
    }

    private boolean isSensitiveHeader(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("authorization")
                || normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("cookie")
                || normalized.contains("api-key")
                || normalized.contains("apikey");
    }

    private String boundedText(String value, String label, int maxLength, boolean allowBlank) {
        String normalized = value == null ? "" : value.trim();
        if (!allowBlank && normalized.isBlank()) {
            throw new InvalidRequestException(label + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new InvalidRequestException(label + "超过长度限制");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
