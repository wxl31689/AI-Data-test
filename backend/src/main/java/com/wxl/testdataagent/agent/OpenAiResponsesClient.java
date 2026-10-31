package com.wxl.testdataagent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class OpenAiResponsesClient implements AiStructuredClient {

    private static final int MAX_TOOL_ARGUMENT_CHARS = 2_000;

    private final OpenAiTransport transport;
    private final ObjectMapper objectMapper;
    private final String model;
    private final boolean enabled;

    OpenAiResponsesClient(
            OpenAiTransport transport,
            ObjectMapper objectMapper,
            @Value("${agent.enabled}") boolean enabled,
            @Value("${agent.openai.model}") String model
    ) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.model = model;
    }

    @Override
    public JsonNode generateStructured(
            String instructions,
            String input,
            String schemaName,
            JsonNode schema
    ) {
        ensureAvailable();
        ObjectNode request = baseRequest(instructions);
        request.put("input", input);
        addStructuredFormat(request, schemaName, schema);
        return extractStructuredOutput(transport.post(request));
    }

    @Override
    public AiToolCall requestToolCall(
            String instructions,
            String input,
            String toolName,
            String toolDescription,
            JsonNode parameters
    ) {
        ensureAvailable();
        ObjectNode request = baseRequest(instructions);
        request.put("input", input);
        request.put("parallel_tool_calls", false);
        ArrayNode tools = request.putArray("tools");
        ObjectNode tool = tools.addObject();
        tool.put("type", "function");
        tool.put("name", toolName);
        tool.put("description", toolDescription);
        tool.set("parameters", parameters);
        tool.put("strict", true);
        ObjectNode toolChoice = request.putObject("tool_choice");
        toolChoice.put("type", "function");
        toolChoice.put("name", toolName);

        JsonNode response = transport.post(request);
        JsonNode output = response.path("output");
        AiToolCall found = null;
        if (output.isArray()) {
            for (JsonNode item : output) {
                if (!"function_call".equals(item.path("type").asText())) continue;
                if (found != null) throw new AiProviderException("模型返回了多个工具调用");
                String arguments = item.path("arguments").asText();
                if (arguments.length() > MAX_TOOL_ARGUMENT_CHARS) {
                    throw new AiProviderException("模型工具参数超过安全大小限制");
                }
                found = new AiToolCall(
                        response.path("id").asText(),
                        item.path("call_id").asText(),
                        item.path("name").asText(),
                        arguments
                );
            }
        }
        if (found == null || found.responseId().isBlank() || found.callId().isBlank()) {
            throw new AiProviderException("模型未返回所需的工具调用");
        }
        return found;
    }

    @Override
    public JsonNode continueStructured(
            AiToolCall toolCall,
            String toolOutput,
            String instructions,
            String schemaName,
            JsonNode schema
    ) {
        ensureAvailable();
        ObjectNode request = baseRequest(instructions);
        request.put("previous_response_id", toolCall.responseId());
        ArrayNode input = request.putArray("input");
        ObjectNode output = input.addObject();
        output.put("type", "function_call_output");
        output.put("call_id", toolCall.callId());
        output.put("output", toolOutput);
        addStructuredFormat(request, schemaName, schema);
        return extractStructuredOutput(transport.post(request));
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public boolean available() {
        return enabled && transport.configured();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean configured() {
        return transport.configured();
    }

    private ObjectNode baseRequest(String instructions) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.put("instructions", instructions);
        request.put("max_output_tokens", 4_000);
        return request;
    }

    private void addStructuredFormat(ObjectNode request, String schemaName, JsonNode schema) {
        ObjectNode format = request.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("strict", true);
        format.set("schema", schema);
    }

    JsonNode extractStructuredOutput(JsonNode response) {
        if ("incomplete".equals(response.path("status").asText())) {
            throw new AiProviderException("模型响应不完整");
        }
        JsonNode output = response.path("output");
        if (!output.isArray()) throw new AiProviderException("模型输出结构无效");
        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if ("refusal".equals(content.path("type").asText())) {
                    throw new AiProviderException("模型拒绝了当前请求");
                }
                if ("output_text".equals(content.path("type").asText())) {
                    String text = content.path("text").asText();
                    try {
                        return objectMapper.readTree(text);
                    } catch (JsonProcessingException exception) {
                        throw new AiProviderException("模型结构化输出不是有效 JSON", exception);
                    }
                }
            }
        }
        throw new AiProviderException("模型没有返回结构化结果");
    }

    private void ensureAvailable() {
        if (!enabled) throw new AgentUnavailableException("AI Agent 已禁用");
        if (!transport.configured()) throw new AgentUnavailableException("AI Agent 未配置 OPENAI_API_KEY");
    }
}
