package com.wxl.testdataagent.dataquality;

record DataExportPayload(
        String fileName,
        String contentType,
        byte[] content
) {
}
