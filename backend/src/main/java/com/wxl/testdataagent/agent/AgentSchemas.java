package com.wxl.testdataagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;


@Component
class AgentSchemas {

    private final ObjectMapper objectMapper;

    AgentSchemas(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode testCaseCandidates() {
        ObjectNode request = objectSchema();
        request.set("properties", properties(
                property("pathParamsJson", stringSchema()),
                property("queryParamsJson", stringSchema()),
                property("headersJson", stringSchema()),
                property("bodyJson", stringSchema()),
                property("timeoutMillis", integerSchema())
        ));
        required(request, "pathParamsJson", "queryParamsJson", "headersJson", "bodyJson", "timeoutMillis");

        ObjectNode assertion = objectSchema();
        assertion.set("properties", properties(
                property("type", enumSchema(
                        "STATUS_CODE", "JSON_PATH_EXISTS", "JSON_VALUE_EQUALS",
                        "JSON_TYPE", "NUMBER_RANGE", "RESPONSE_TIME"
                )),
                property("path", stringSchema()),
                property("expectedJson", stringSchema()),
                property("expectedType", enumSchema(
                        "NONE", "STRING", "NUMBER", "INTEGER", "BOOLEAN", "OBJECT", "ARRAY", "NULL"
                )),
                property("min", stringSchema()),
                property("max", stringSchema()),
                property("maxMillis", integerSchema())
        ));
        required(assertion, "type", "path", "expectedJson", "expectedType", "min", "max", "maxMillis");

        ObjectNode candidate = objectSchema();
        candidate.set("properties", properties(
                property("name", stringSchema()),
                property("scenarioType", enumSchema("POSITIVE", "NEGATIVE", "BOUNDARY")),
                property("priority", enumSchema("P0", "P1", "P2")),
                property("request", request),
                property("assertions", arraySchema(assertion)),
                property("rationale", stringSchema()),
                property("risk", stringSchema())
        ));
        required(candidate, "name", "scenarioType", "priority", "request", "assertions", "rationale", "risk");

        ObjectNode root = objectSchema();
        root.set("properties", properties(property("candidates", arraySchema(candidate))));
        required(root, "candidates");
        return root;
    }

    JsonNode failureAnalysis() {
        ObjectNode cause = objectSchema();
        cause.set("properties", properties(
                property("testResultId", stringSchema()),
                property("category", enumSchema(
                        "ASSERTION_MISMATCH", "HTTP_ERROR", "TIMEOUT_OR_NETWORK",
                        "TEST_DATA", "CONTRACT_MISMATCH", "UNKNOWN"
                )),
                property("evidence", stringSchema()),
                property("explanation", stringSchema()),
                property("confidence", enumSchema("HIGH", "MEDIUM", "LOW")),
                property("suggestedAction", stringSchema())
        ));
        required(cause, "testResultId", "category", "evidence", "explanation", "confidence", "suggestedAction");

        ObjectNode root = objectSchema();
        root.set("properties", properties(
                property("summary", stringSchema()),
                property("rootCauses", arraySchema(cause)),
                property("recommendations", arraySchema(stringSchema())),
                property("limitations", arraySchema(stringSchema()))
        ));
        required(root, "summary", "rootCauses", "recommendations", "limitations");
        return root;
    }

    JsonNode runEvidenceToolParameters() {
        ObjectNode root = objectSchema();
        root.set("properties", properties(property("runId", stringSchema())));
        required(root, "runId");
        return root;
    }

    JsonNode executeTestRunToolParameters() {
        ObjectNode root = objectSchema();
        root.set("properties", properties(
                property("approvalId", stringSchema()),
                property("projectId", stringSchema()),
                property("caseIds", arraySchema(stringSchema())),
                property("runName", stringSchema()),
                property("clientRequestId", stringSchema())
        ));
        required(root, "approvalId", "projectId", "caseIds", "runName", "clientRequestId");
        return root;
    }

    JsonNode executionAcknowledgement() {
        ObjectNode root = objectSchema();
        root.set("properties", properties(
                property("runId", stringSchema()),
                property("summary", stringSchema())
        ));
        required(root, "runId", "summary");
        return root;
    }

    private ObjectNode objectSchema() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);
        return node;
    }

    private ObjectNode stringSchema() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "string");
        return node;
    }

    private ObjectNode integerSchema() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "integer");
        return node;
    }

    private ObjectNode enumSchema(String... values) {
        ObjectNode node = stringSchema();
        ArrayNode options = node.putArray("enum");
        for (String value : values) options.add(value);
        return node;
    }

    private ObjectNode arraySchema(JsonNode items) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "array");
        node.set("items", items);
        return node;
    }

    private ObjectNode properties(MapEntry... entries) {
        ObjectNode properties = objectMapper.createObjectNode();
        for (MapEntry entry : entries) properties.set(entry.name(), entry.schema());
        return properties;
    }

    private MapEntry property(String name, JsonNode schema) {
        return new MapEntry(name, schema);
    }

    private void required(ObjectNode node, String... names) {
        ArrayNode required = node.putArray("required");
        for (String name : names) required.add(name);
    }

    private record MapEntry(String name, JsonNode schema) {
    }
}
