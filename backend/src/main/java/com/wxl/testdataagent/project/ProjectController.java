package com.wxl.testdataagent.project;

import com.wxl.testdataagent.common.ApiResponse;
import com.wxl.testdataagent.common.RequestTrace;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.created(projectService.create(body), RequestTrace.get(request));
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(HttpServletRequest request) {
        return ApiResponse.ok(projectService.list(), RequestTrace.get(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> get(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(projectService.get(id), RequestTrace.get(request));
    }
}

