package com.wxl.testdataagent.dataquality;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DataQualityTaskResponse(
        String id,
        String projectId,
        String fileName,
        DataFileType fileType,
        long fileSize,
        String sheetName,
        int rowCount,
        int columnCount,
        long emptyCellCount,
        double emptyCellRate,
        DataQualityStatus status,
        List<String> headers,
        List<Map<String, String>> previewRows,
        Map<String, Object> summary,
        List<DataQualityIssueResponse> issues,
        Instant createdAt,
        Instant updatedAt
) {
}
