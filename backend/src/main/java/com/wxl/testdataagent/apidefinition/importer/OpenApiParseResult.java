package com.wxl.testdataagent.apidefinition.importer;

import java.util.List;

public record OpenApiParseResult(
        String title,
        String version,
        List<ParsedApiDefinition> definitions,
        List<String> warnings
) {
}

