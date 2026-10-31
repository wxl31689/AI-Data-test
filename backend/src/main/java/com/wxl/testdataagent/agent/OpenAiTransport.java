package com.wxl.testdataagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

interface OpenAiTransport {
    JsonNode post(ObjectNode request);

    boolean configured();
}
