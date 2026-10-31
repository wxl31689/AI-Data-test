package com.wxl.testdataagent.apidefinition;

import java.util.List;

public record OpenApiImportResponse(
        String documentTitle,
        String documentVersion,
        int importedCount,
        int updatedCount,
        List<String> warnings,
        List<ApiDefinitionResponse> definitions
) {
}

