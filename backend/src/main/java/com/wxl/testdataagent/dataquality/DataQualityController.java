package com.wxl.testdataagent.dataquality;

import com.wxl.testdataagent.common.ApiResponse;
import com.wxl.testdataagent.common.RequestTrace;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/data-quality/tasks")
public class DataQualityController {

    private final DataQualityService dataQualityService;

    public DataQualityController(DataQualityService dataQualityService) {
        this.dataQualityService = dataQualityService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DataQualityTaskResponse> upload(
            @RequestParam String projectId,
            @RequestParam(required = false) String sheetName,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request
    ) {
        return ApiResponse.created(
                dataQualityService.upload(projectId, file, sheetName),
                RequestTrace.get(request)
        );
    }

    @GetMapping
    public ApiResponse<List<DataQualityTaskResponse>> list(
            @RequestParam String projectId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(dataQualityService.list(projectId), RequestTrace.get(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<DataQualityTaskResponse> get(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(dataQualityService.get(id), RequestTrace.get(request));
    }

    @PostMapping("/{id}/analyze")
    public ApiResponse<DataQualityTaskResponse> analyze(
            @PathVariable String id,
            @Valid @RequestBody AnalyzeDataQualityRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(dataQualityService.analyze(id, body), RequestTrace.get(request));
    }

    @PostMapping("/{id}/clean-preview")
    public ApiResponse<CleaningPreviewResponse> previewCleaning(
            @PathVariable String id,
            @Valid @RequestBody CleanDataRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                dataQualityService.previewCleaning(id, body),
                RequestTrace.get(request)
        );
    }

    @PostMapping("/{id}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String id,
            @RequestParam DataExportFormat format,
            @RequestParam(defaultValue = "CLEANED") DataExportDataset dataset,
            @Valid @RequestBody(required = false) CleanDataRequest body
    ) {
        DataExportPayload payload = dataQualityService.export(id, format, dataset, body);
        String disposition = ContentDisposition.attachment()
                .filename(payload.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .contentLength(payload.content().length)
                .body(payload.content());
    }
}
