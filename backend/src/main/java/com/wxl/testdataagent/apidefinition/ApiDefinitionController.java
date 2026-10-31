package com.wxl.testdataagent.apidefinition;

import com.wxl.testdataagent.common.ApiResponse;
import com.wxl.testdataagent.common.RequestTrace;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/api-definitions")
public class ApiDefinitionController {

    private final ApiDefinitionService apiDefinitionService;

    public ApiDefinitionController(ApiDefinitionService apiDefinitionService) {
        this.apiDefinitionService = apiDefinitionService;
    }

    @GetMapping
    public ApiResponse<List<ApiDefinitionResponse>> list(
            @RequestParam String projectId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(apiDefinitionService.list(projectId), RequestTrace.get(request));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OpenApiImportResponse> importOpenApi(
            @RequestParam String projectId,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                apiDefinitionService.importOpenApi(projectId, file),
                RequestTrace.get(request)
        );
    }
}

