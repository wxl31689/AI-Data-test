package com.wxl.testdataagent.testrun;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/test-runs")
public class TestRunController {

    private final TestRunService testRunService;
    private final TestRunReportService reportService;

    public TestRunController(TestRunService testRunService, TestRunReportService reportService) {
        this.testRunService = testRunService;
        this.reportService = reportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TestRunResponse> execute(
            @Valid @RequestBody CreateTestRunRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.accepted(testRunService.create(body), RequestTrace.get(request));
    }

    @GetMapping
    public ApiResponse<List<TestRunResponse>> list(
            @RequestParam String projectId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(testRunService.list(projectId), RequestTrace.get(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<TestRunResponse> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(testRunService.get(id), RequestTrace.get(request));
    }

    @GetMapping("/{id}/results")
    public ApiResponse<List<TestResultResponse>> results(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(testRunService.getResults(id), RequestTrace.get(request));
    }

    @PostMapping("/{id}/rerun-failed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TestRunResponse> rerunFailed(
            @PathVariable String id,
            @Valid @RequestBody(required = false) RerunFailedRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.accepted(
                testRunService.rerunFailed(id, body == null ? new RerunFailedRequest(null) : body),
                RequestTrace.get(request)
        );
    }

    @GetMapping("/{id}/report")
    public ApiResponse<TestRunReportResponse> report(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(reportService.getReport(id), RequestTrace.get(request));
    }

    @GetMapping("/{id}/report/export")
    public ResponseEntity<byte[]> exportReport(@PathVariable String id) {
        byte[] content = reportService.exportHtml(id);
        String disposition = ContentDisposition.attachment()
                .filename("test-run-report-" + id + ".html", StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
                .contentLength(content.length)
                .body(content);
    }
}

