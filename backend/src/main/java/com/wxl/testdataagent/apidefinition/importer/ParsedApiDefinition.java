package com.wxl.testdataagent.apidefinition.importer;

public record ParsedApiDefinition(
        String name,
        String path,
        String method,
        String summary,
        String baseUrl,
        String parametersJson,
        String requestSchemaJson,
        String responseSchemaJson
) {
}

