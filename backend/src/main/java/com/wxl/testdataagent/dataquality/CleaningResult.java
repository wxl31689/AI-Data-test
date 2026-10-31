package com.wxl.testdataagent.dataquality;

import java.util.List;

record CleaningResult(
        ParsedDataset dataset,
        long changedCellCount,
        int removedDuplicateCount,
        List<Integer> removedDuplicateRowNumbers,
        List<CleaningChange> changes
) {
}
