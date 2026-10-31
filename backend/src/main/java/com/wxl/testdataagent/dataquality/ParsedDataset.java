package com.wxl.testdataagent.dataquality;

import java.util.List;

public record ParsedDataset(
        List<String> headers,
        List<List<String>> rows,
        String sheetName
) {
}
