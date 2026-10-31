package com.wxl.testdataagent.dataquality;

import java.util.List;
import java.util.Map;

public record CleaningPreviewResponse(
        String taskId,
        int originalRowCount,
        int outputRowCount,
        long changedCellCount,
        int removedDuplicateCount,
        List<Integer> removedDuplicateRowNumbers,
        List<String> headers,
        List<Map<String, String>> previewRows,
        List<CleaningChange> changes
) {
}
