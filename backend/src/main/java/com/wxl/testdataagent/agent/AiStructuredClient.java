package com.wxl.testdataagent.agent;

import com.fasterxml.jackson.databind.JsonNode;

interface AiStructuredClient {
    JsonNode generateStructured(
            String instructions,
            String input,
            String schemaName,
            JsonNode schema
    );

    AiToolCall requestToolCall(
            String instructions,
            String input,
            String toolName,
            String toolDescription,
            JsonNode parameters
    );

    JsonNode continueStructured(
            AiToolCall toolCall,
            String toolOutput,
            String instructions,
            String schemaName,
            JsonNode schema
    );

    String model();

    boolean available();

    boolean enabled();

    boolean configured();
}
