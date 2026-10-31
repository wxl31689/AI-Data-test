package com.wxl.testdataagent.testengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.wxl.testdataagent.testcase.AssertionConfig;
import com.wxl.testdataagent.testcase.AssertionType;
import com.wxl.testdataagent.testcase.JsonValueType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

@Component
public class AssertionEngine {

    private final ObjectMapper objectMapper;

    public AssertionEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<AssertionEvaluation> evaluate(
            int statusCode,
            String responseBody,
            long durationMs,
            List<AssertionConfig> assertions
    ) {
        List<AssertionEvaluation> results = new ArrayList<>();
        for (AssertionConfig assertion : assertions) {
            results.add(evaluateOne(statusCode, responseBody, durationMs, assertion));
        }
        return results;
    }

    public List<AssertionEvaluation> evaluate(
            int statusCode,
            String responseBody,
            List<AssertionConfig> assertions
    ) {
        return evaluate(statusCode, responseBody, 0, assertions);
    }

    private AssertionEvaluation evaluateOne(
            int statusCode,
            String responseBody,
            long durationMs,
            AssertionConfig assertion
    ) {
        if (assertion.type() == AssertionType.STATUS_CODE) {
            int expected = assertion.expected().asInt();
            boolean passed = statusCode == expected;
            return new AssertionEvaluation(
                    assertion.type().name(),
                    String.valueOf(expected),
                    String.valueOf(statusCode),
                    passed,
                    passed ? "HTTP 状态码符合预期" : "HTTP 状态码不符合预期"
            );
        }

        if (assertion.type() == AssertionType.RESPONSE_TIME) {
            long expected = assertion.maxMillis();
            boolean passed = durationMs <= expected;
            return new AssertionEvaluation(
                    assertion.type().name(),
                    "<= " + expected + " ms",
                    durationMs + " ms",
                    passed,
                    passed ? "响应耗时在阈值内" : "响应耗时超过阈值"
            );
        }

        try {
            Object actual = JsonPath.read(responseBody, assertion.path());
            if (assertion.type() == AssertionType.JSON_PATH_EXISTS) {
                return new AssertionEvaluation(
                        assertion.type().name(),
                        "字段存在",
                        render(actual),
                        true,
                        "JSONPath 可以定位到字段"
                );
            }

            JsonNode actualNode = objectMapper.valueToTree(actual);
            if (assertion.type() == AssertionType.JSON_TYPE) {
                JsonValueType actualType = detectType(actualNode);
                boolean passed = actualType == assertion.expectedType();
                return new AssertionEvaluation(
                        assertion.type().name(),
                        assertion.expectedType().name(),
                        actualType.name(),
                        passed,
                        passed ? "JSON 字段类型符合预期" : "JSON 字段类型不符合预期"
                );
            }
            if (assertion.type() == AssertionType.NUMBER_RANGE) {
                return evaluateNumberRange(assertion, actualNode);
            }
            boolean passed = assertion.expected().equals(actualNode);
            return new AssertionEvaluation(
                    assertion.type().name(),
                    render(assertion.expected()),
                    render(actualNode),
                    passed,
                    passed ? "字段值符合预期" : "字段值不符合预期"
            );
        } catch (PathNotFoundException exception) {
            return new AssertionEvaluation(
                    assertion.type().name(),
                    assertion.type() == AssertionType.JSON_PATH_EXISTS
                            ? "字段存在"
                            : render(assertion.expected()),
                    "路径不存在",
                    false,
                    "JSONPath 无法定位: " + assertion.path()
            );
        } catch (Exception exception) {
            return new AssertionEvaluation(
                    assertion.type().name(),
                    render(assertion.expected()),
                    "响应无法解析",
                    false,
                    "JSON 断言执行失败"
            );
        }
    }

    private AssertionEvaluation evaluateNumberRange(AssertionConfig assertion, JsonNode actualNode) {
        if (!actualNode.isNumber()) {
            return new AssertionEvaluation(
                    assertion.type().name(),
                    rangeText(assertion),
                    render(actualNode),
                    false,
                    "JSON 字段不是数值"
            );
        }
        BigDecimal actual = actualNode.decimalValue();
        boolean meetsMin = assertion.min() == null || actual.compareTo(assertion.min()) >= 0;
        boolean meetsMax = assertion.max() == null || actual.compareTo(assertion.max()) <= 0;
        boolean passed = meetsMin && meetsMax;
        return new AssertionEvaluation(
                assertion.type().name(),
                rangeText(assertion),
                actual.stripTrailingZeros().toPlainString(),
                passed,
                passed ? "数值在允许范围内" : "数值超出允许范围"
        );
    }

    private String rangeText(AssertionConfig assertion) {
        String min = assertion.min() == null ? "-∞" : assertion.min().stripTrailingZeros().toPlainString();
        String max = assertion.max() == null ? "+∞" : assertion.max().stripTrailingZeros().toPlainString();
        return "[" + min + ", " + max + "]";
    }

    private JsonValueType detectType(JsonNode node) {
        if (node.isTextual()) return JsonValueType.STRING;
        if (node.isIntegralNumber()) return JsonValueType.INTEGER;
        if (node.isNumber()) return JsonValueType.NUMBER;
        if (node.isBoolean()) return JsonValueType.BOOLEAN;
        if (node.isObject()) return JsonValueType.OBJECT;
        if (node.isArray()) return JsonValueType.ARRAY;
        return JsonValueType.NULL;
    }

    private String render(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof JsonNode node && node.isTextual()) {
            return node.asText();
        }
        return String.valueOf(value);
    }
}

