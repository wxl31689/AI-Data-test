package com.wxl.testdataagent.apidefinition.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.wxl.testdataagent.common.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class OpenApiDocumentParser {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "delete", "patch", "head", "options", "trace"
    );

    private final ObjectMapper objectMapper;

    public OpenApiDocumentParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OpenApiParseResult parse(byte[] content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            validateRoot(root);

            List<String> warnings = new ArrayList<>();
            if (root.has("webhooks")) {
                warnings.add("当前版本不导入 OpenAPI webhooks");
            }

            String baseUrl = root.path("servers").path(0).path("url").asText(null);
            if (baseUrl == null || baseUrl.isBlank()) {
                warnings.add("文档未声明 servers，执行前需要补充接口基础地址");
            }

            List<ParsedApiDefinition> definitions = parsePaths(root.path("paths"), baseUrl, warnings);
            if (definitions.isEmpty()) {
                throw new InvalidRequestException("OpenAPI 文档没有可导入的 HTTP 操作");
            }

            return new OpenApiParseResult(
                    root.path("info").path("title").asText("未命名 OpenAPI 文档"),
                    root.path("info").path("version").asText("unknown"),
                    List.copyOf(definitions),
                    List.copyOf(warnings)
            );
        } catch (IOException exception) {
            throw new InvalidRequestException("文件不是合法的 JSON", exception);
        }
    }

    private void validateRoot(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new InvalidRequestException("OpenAPI 文档根节点必须是 JSON 对象");
        }
        String version = root.path("openapi").asText();
        if (!version.startsWith("3.")) {
            throw new InvalidRequestException("当前版本仅支持 OpenAPI 3.x JSON");
        }
        if (!root.path("paths").isObject()) {
            throw new InvalidRequestException("OpenAPI 文档缺少 paths 对象");
        }
    }

    private List<ParsedApiDefinition> parsePaths(
            JsonNode pathsNode,
            String baseUrl,
            List<String> warnings
    ) throws JsonProcessingException {
        List<ParsedApiDefinition> definitions = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> paths = pathsNode.properties().iterator();

        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = paths.next();
            String path = pathEntry.getKey();
            JsonNode pathNode = pathEntry.getValue();
            if (!pathNode.isObject()) {
                warnings.add("已跳过非对象路径定义: " + path);
                continue;
            }

            for (String method : HTTP_METHODS) {
                JsonNode operation = pathNode.get(method);
                if (operation == null || !operation.isObject()) {
                    continue;
                }
                if (operation.has("callbacks")) {
                    warnings.add(method.toUpperCase(Locale.ROOT) + " " + path + " 的 callbacks 暂未导入");
                }

                String summary = operation.path("summary").asText(null);
                String operationId = operation.path("operationId").asText(null);
                String name = firstNonBlank(operationId, summary, method.toUpperCase(Locale.ROOT) + " " + path);

                ArrayNode parameters = objectMapper.createArrayNode();
                appendArray(parameters, pathNode.path("parameters"));
                appendArray(parameters, operation.path("parameters"));

                definitions.add(new ParsedApiDefinition(
                        name,
                        path,
                        method.toUpperCase(Locale.ROOT),
                        summary,
                        baseUrl,
                        objectMapper.writeValueAsString(parameters),
                        toJson(extractRequestSchema(operation)),
                        toJson(extractResponseSchema(operation))
                ));
            }
        }
        return definitions;
    }

    private void appendArray(ArrayNode target, JsonNode source) {
        if (source.isArray()) {
            source.forEach(target::add);
        }
    }

    private JsonNode extractRequestSchema(JsonNode operation) {
        JsonNode content = operation.path("requestBody").path("content");
        return extractSchemaFromContent(content);
    }

    private JsonNode extractResponseSchema(JsonNode operation) {
        JsonNode responses = operation.path("responses");
        if (!responses.isObject()) {
            return null;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = responses.properties().iterator();
        JsonNode fallback = null;
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> response = fields.next();
            JsonNode schema = extractSchemaFromContent(response.getValue().path("content"));
            if (schema == null) {
                continue;
            }
            if (response.getKey().startsWith("2")) {
                return schema;
            }
            if (fallback == null) {
                fallback = schema;
            }
        }
        return fallback;
    }

    private JsonNode extractSchemaFromContent(JsonNode content) {
        if (!content.isObject()) {
            return null;
        }
        JsonNode jsonSchema = content.path("application/json").path("schema");
        if (!jsonSchema.isMissingNode()) {
            return jsonSchema;
        }

        Iterator<JsonNode> mediaTypes = content.elements();
        while (mediaTypes.hasNext()) {
            JsonNode schema = mediaTypes.next().path("schema");
            if (!schema.isMissingNode()) {
                return schema;
            }
        }
        return null;
    }

    private String toJson(JsonNode node) throws JsonProcessingException {
        return node == null ? null : objectMapper.writeValueAsString(node);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "未命名接口";
    }
}

