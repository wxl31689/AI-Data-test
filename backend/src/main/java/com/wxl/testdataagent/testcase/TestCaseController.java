package com.wxl.testdataagent.testcase;

import com.wxl.testdataagent.common.ApiResponse;
import com.wxl.testdataagent.common.RequestTrace;
import com.wxl.testdataagent.common.LocalApprovalGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@RestController
@RequestMapping("/api/v1/test-cases")
public class TestCaseController {

    private final TestCaseService testCaseService;
    private final LocalApprovalGuard approvalGuard;

    public TestCaseController(TestCaseService testCaseService, LocalApprovalGuard approvalGuard) {
        this.testCaseService = testCaseService;
        this.approvalGuard = approvalGuard;
    }

    @PostMapping("/{id}/review")
    public ApiResponse<TestCaseResponse> review(
            @PathVariable String id,
            @Valid @RequestBody ReviewTestCaseRequest body,
            @RequestHeader(value = LocalApprovalGuard.HEADER, required = false) String approvalToken,
            HttpServletRequest request
    ) {
        approvalGuard.requireApproval(request, approvalToken);
        return ApiResponse.ok(testCaseService.review(id), RequestTrace.get(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TestCaseResponse> create(
            @Valid @RequestBody CreateTestCaseRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.created(testCaseService.create(body), RequestTrace.get(request));
    }

    @GetMapping
    public ApiResponse<List<TestCaseResponse>> list(
            @RequestParam String projectId,
            @RequestParam(required = false) String apiDefinitionId,
            @RequestParam(required = false) ReviewStatus reviewStatus,
            @RequestParam(required = false) Boolean enabled,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                testCaseService.list(projectId, apiDefinitionId, reviewStatus, enabled),
                RequestTrace.get(request)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<TestCaseResponse> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(testCaseService.get(id), RequestTrace.get(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TestCaseResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateTestCaseRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(testCaseService.update(id, body), RequestTrace.get(request));
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<TestCaseResponse> setEnabled(
            @PathVariable String id,
            @Valid @RequestBody SetCaseEnabledRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(testCaseService.setEnabled(id, body.enabled()), RequestTrace.get(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        testCaseService.delete(id);
    }
}
