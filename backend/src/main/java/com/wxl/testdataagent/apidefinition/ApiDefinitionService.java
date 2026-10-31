package com.wxl.testdataagent.apidefinition;

import com.wxl.testdataagent.apidefinition.importer.OpenApiDocumentParser;
import com.wxl.testdataagent.apidefinition.importer.OpenApiParseResult;
import com.wxl.testdataagent.apidefinition.importer.ParsedApiDefinition;
import com.wxl.testdataagent.common.InvalidRequestException;
import com.wxl.testdataagent.common.ResourceNotFoundException;
import com.wxl.testdataagent.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ApiDefinitionService {

    private static final long MAX_OPENAPI_FILE_SIZE = 2L * 1024 * 1024;

    private final ApiDefinitionRepository apiDefinitionRepository;
    private final ProjectRepository projectRepository;
    private final OpenApiDocumentParser parser;

    public ApiDefinitionService(
            ApiDefinitionRepository apiDefinitionRepository,
            ProjectRepository projectRepository,
            OpenApiDocumentParser parser
    ) {
        this.apiDefinitionRepository = apiDefinitionRepository;
        this.projectRepository = projectRepository;
        this.parser = parser;
    }

    @Transactional(readOnly = true)
    public List<ApiDefinitionResponse> list(String projectId) {
        ensureProjectExists(projectId);
        return apiDefinitionRepository.findByProjectIdOrderByPathAscMethodAsc(projectId).stream()
                .map(ApiDefinitionResponse::from)
                .toList();
    }

    @Transactional
    public OpenApiImportResponse importOpenApi(String projectId, MultipartFile file) {
        ensureProjectExists(projectId);
        validateFile(file);

        OpenApiParseResult parseResult;
        try {
            parseResult = parser.parse(file.getBytes());
        } catch (IOException exception) {
            throw new InvalidRequestException("无法读取上传文件", exception);
        }

        int importedCount = 0;
        int updatedCount = 0;
        List<ApiDefinitionEntity> affected = new ArrayList<>();

        for (ParsedApiDefinition parsed : parseResult.definitions()) {
            ApiDefinitionEntity entity = apiDefinitionRepository
                    .findByProjectIdAndMethodAndPath(projectId, parsed.method(), parsed.path())
                    .orElse(null);

            if (entity == null) {
                entity = new ApiDefinitionEntity(
                        projectId,
                        parsed.name(),
                        parsed.path(),
                        parsed.method(),
                        parsed.summary(),
                        parsed.baseUrl(),
                        parsed.parametersJson(),
                        parsed.requestSchemaJson(),
                        parsed.responseSchemaJson()
                );
                importedCount++;
            } else {
                entity.update(
                        parsed.name(),
                        parsed.summary(),
                        parsed.baseUrl(),
                        parsed.parametersJson(),
                        parsed.requestSchemaJson(),
                        parsed.responseSchemaJson()
                );
                updatedCount++;
            }
            affected.add(entity);
        }

        List<ApiDefinitionResponse> saved = apiDefinitionRepository.saveAll(affected).stream()
                .map(ApiDefinitionResponse::from)
                .toList();
        return new OpenApiImportResponse(
                parseResult.title(),
                parseResult.version(),
                importedCount,
                updatedCount,
                parseResult.warnings(),
                saved
        );
    }

    private void ensureProjectExists(String projectId) {
        if (projectId == null || projectId.isBlank() || !projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("项目不存在");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("请选择 OpenAPI JSON 文件");
        }
        if (file.getSize() > MAX_OPENAPI_FILE_SIZE) {
            throw new InvalidRequestException("OpenAPI 文件不能超过 2 MB");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new InvalidRequestException("当前版本仅支持 .json 文件");
        }
    }
}

