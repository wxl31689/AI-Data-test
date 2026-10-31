package com.wxl.testdataagent.dataquality;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wxl.testdataagent.common.InvalidRequestException;
import com.wxl.testdataagent.common.ResourceNotFoundException;
import com.wxl.testdataagent.project.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DataQualityService {

    private final DataQualityTaskRepository taskRepository;
    private final DataQualityIssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final DataFileParser fileParser;
    private final DataQualityRuleEngine ruleEngine;
    private final DataCleaningService cleaningService;
    private final DataExportService exportService;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;
    private final long maxFileBytes;
    private final int previewRows;

    public DataQualityService(
            DataQualityTaskRepository taskRepository,
            DataQualityIssueRepository issueRepository,
            ProjectRepository projectRepository,
            DataFileParser fileParser,
            DataQualityRuleEngine ruleEngine,
            DataCleaningService cleaningService,
            DataExportService exportService,
            ObjectMapper objectMapper,
            @Value("${data-quality.storage-dir}") String storageDir,
            @Value("${data-quality.max-file-bytes}") long maxFileBytes,
            @Value("${data-quality.preview-rows}") int previewRows
    ) {
        this.taskRepository = taskRepository;
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.fileParser = fileParser;
        this.ruleEngine = ruleEngine;
        this.cleaningService = cleaningService;
        this.exportService = exportService;
        this.objectMapper = objectMapper;
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
        this.maxFileBytes = maxFileBytes;
        this.previewRows = previewRows;
    }

    public DataQualityTaskResponse upload(
            String projectId,
            MultipartFile file,
            String requestedSheetName
    ) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("项目不存在");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("上传文件不能为空");
        }
        if (file.getSize() > maxFileBytes) {
            throw new InvalidRequestException("上传文件超过 " + (maxFileBytes / 1024 / 1024) + " MB 上限");
        }

        String safeOriginalName = safeOriginalName(file.getOriginalFilename());
        DataFileType fileType = detectFileType(safeOriginalName);
        byte[] content = readContent(file);
        validateContent(content, fileType, file.getContentType());
        String storedFileName = UUID.randomUUID() + (fileType == DataFileType.CSV ? ".csv" : ".xlsx");
        Path storedPath = resolveStoragePath(storedFileName);

        try {
            Files.createDirectories(storageRoot);
            Files.write(storedPath, content, StandardOpenOption.CREATE_NEW);
            ParsedDataset dataset = fileParser.parse(storedPath, fileType, requestedSheetName);
            long emptyCellCount = countEmptyCells(dataset);
            Map<String, Object> initialSummary = new LinkedHashMap<>();
            initialSummary.put("issueCount", 0);
            initialSummary.put("byRule", Map.of());
            initialSummary.put("byColumn", Map.of());
            initialSummary.put("emptyCellRate", emptyCellRate(dataset, emptyCellCount));

            DataQualityTaskEntity task = taskRepository.save(new DataQualityTaskEntity(
                    projectId,
                    safeOriginalName,
                    storedFileName,
                    fileType,
                    file.getSize(),
                    dataset.sheetName(),
                    dataset.rows().size(),
                    dataset.headers().size(),
                    emptyCellCount,
                    writeJson(dataset.headers()),
                    writeJson(buildPreview(dataset)),
                    writeJson(initialSummary)
            ));
            return toResponse(task, List.of());
        } catch (InvalidRequestException exception) {
            deleteQuietly(storedPath);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(storedPath);
            throw new InvalidRequestException("文件保存失败", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<DataQualityTaskResponse> list(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("项目不存在");
        }
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(task -> toResponse(task, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DataQualityTaskResponse get(String id) {
        DataQualityTaskEntity task = findTask(id);
        return toResponse(task, issueRepository.findByTaskIdOrderByRowNumberAscColumnNameAsc(id));
    }

    @Transactional
    public DataQualityTaskResponse analyze(String id, AnalyzeDataQualityRequest request) {
        DataQualityTaskEntity task = findTask(id);
        ParsedDataset dataset = fileParser.parse(
                resolveStoragePath(task.getStoredFileName()),
                task.getFileType(),
                task.getSheetName()
        );
        List<DetectedQualityIssue> detected = ruleEngine.analyze(
                dataset,
                request.columns(),
                request.duplicateKeyColumns()
        );

        issueRepository.deleteByTaskId(id);
        issueRepository.flush();
        List<DataQualityIssueEntity> entities = issueRepository.saveAll(detected.stream()
                .map(issue -> new DataQualityIssueEntity(
                        id,
                        issue.rowNumber(),
                        issue.columnName(),
                        issue.ruleCode(),
                        issue.originalValue(),
                        issue.severity(),
                        issue.description()
                ))
                .toList());
        task.markAnalyzed(writeJson(buildAnalysisSummary(
                task,
                entities,
                request.columns(),
                request.duplicateKeyColumns()
        )));
        taskRepository.save(task);
        return toResponse(task, entities);
    }

    @Transactional(readOnly = true)
    public CleaningPreviewResponse previewCleaning(String id, CleanDataRequest request) {
        DataQualityTaskEntity task = findTask(id);
        ParsedDataset source = readDataset(task);
        CleaningResult result = cleaningService.clean(source, request);
        return new CleaningPreviewResponse(
                task.getId(),
                source.rows().size(),
                result.dataset().rows().size(),
                result.changedCellCount(),
                result.removedDuplicateCount(),
                result.removedDuplicateRowNumbers(),
                result.dataset().headers(),
                buildPreview(result.dataset()),
                result.changes()
        );
    }

    @Transactional(readOnly = true)
    public DataExportPayload export(
            String id,
            DataExportFormat format,
            DataExportDataset datasetType,
            CleanDataRequest request
    ) {
        DataQualityTaskEntity task = findTask(id);
        ParsedDataset source = readDataset(task);
        ParsedDataset exportDataset = datasetType == DataExportDataset.CLEANED
                ? cleaningService.clean(source, request == null ? CleanDataRequest.empty() : request).dataset()
                : source;
        byte[] content = exportService.export(exportDataset, format, readQualityRules(task));
        String baseName = removeExtension(task.getFileName());
        String suffix = datasetType == DataExportDataset.CLEANED ? "-cleaned" : "-original";
        String extension = format == DataExportFormat.CSV ? ".csv" : ".xlsx";
        String contentType = format == DataExportFormat.CSV
                ? "text/csv; charset=UTF-8"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return new DataExportPayload(baseName + suffix + extension, contentType, content);
    }

    private Map<String, Object> buildAnalysisSummary(
            DataQualityTaskEntity task,
            List<DataQualityIssueEntity> issues,
            List<ColumnQualityRule> columns,
            List<String> duplicateKeyColumns
    ) {
        Map<String, Long> byRule = issues.stream().collect(Collectors.groupingBy(
                issue -> issue.getRuleCode().name(),
                LinkedHashMap::new,
                Collectors.counting()
        ));
        Map<String, Long> byColumn = issues.stream().collect(Collectors.groupingBy(
                DataQualityIssueEntity::getColumnName,
                LinkedHashMap::new,
                Collectors.counting()
        ));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("issueCount", issues.size());
        summary.put("byRule", byRule);
        summary.put("byColumn", byColumn);
        summary.put("emptyCellRate", emptyCellRate(task));
        summary.put("columns", columns);
        summary.put("duplicateKeyColumns", duplicateKeyColumns == null ? List.of() : duplicateKeyColumns);
        summary.put("analyzedAt", Instant.now().toString());
        return summary;
    }

    private List<Map<String, String>> buildPreview(ParsedDataset dataset) {
        List<Map<String, String>> preview = new ArrayList<>();
        int limit = Math.min(previewRows, dataset.rows().size());
        for (int rowIndex = 0; rowIndex < limit; rowIndex++) {
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < dataset.headers().size(); column++) {
                row.put(dataset.headers().get(column), dataset.rows().get(rowIndex).get(column));
            }
            preview.add(row);
        }
        return List.copyOf(preview);
    }

    private ParsedDataset readDataset(DataQualityTaskEntity task) {
        return fileParser.parse(
                resolveStoragePath(task.getStoredFileName()),
                task.getFileType(),
                task.getSheetName()
        );
    }

    private List<ColumnQualityRule> readQualityRules(DataQualityTaskEntity task) {
        Map<String, Object> summary = readJson(task.getSummaryJson(), new TypeReference<>() {});
        if (summary == null || summary.get("columns") == null) return List.of();
        return objectMapper.convertValue(
                summary.get("columns"),
                new TypeReference<List<ColumnQualityRule>>() {}
        );
    }

    private String removeExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
    }

    private long countEmptyCells(ParsedDataset dataset) {
        return dataset.rows().stream()
                .flatMap(List::stream)
                .filter(value -> value == null || value.trim().isEmpty())
                .count();
    }

    private double emptyCellRate(ParsedDataset dataset, long emptyCellCount) {
        long total = (long) dataset.rows().size() * dataset.headers().size();
        return total == 0 ? 0 : (double) emptyCellCount / total;
    }

    private double emptyCellRate(DataQualityTaskEntity task) {
        long total = (long) task.getRowCount() * task.getColumnCount();
        return total == 0 ? 0 : (double) task.getEmptyCellCount() / total;
    }

    private DataQualityTaskResponse toResponse(
            DataQualityTaskEntity task,
            List<DataQualityIssueEntity> issues
    ) {
        return new DataQualityTaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getFileName(),
                task.getFileType(),
                task.getFileSize(),
                task.getSheetName(),
                task.getRowCount(),
                task.getColumnCount(),
                task.getEmptyCellCount(),
                emptyCellRate(task),
                task.getStatus(),
                readJson(task.getHeadersJson(), new TypeReference<>() {}),
                readJson(task.getPreviewJson(), new TypeReference<>() {}),
                readJson(task.getSummaryJson(), new TypeReference<>() {}),
                issues.stream().map(DataQualityIssueResponse::from).toList(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private DataQualityTaskEntity findTask(String id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("数据质量任务不存在"));
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new InvalidRequestException("无法读取上传文件", exception);
        }
    }

    private String safeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new InvalidRequestException("文件名不能为空");
        }
        String normalized = originalName.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (baseName.isBlank() || baseName.length() > 255) {
            throw new InvalidRequestException("文件名无效或过长");
        }
        return baseName;
    }

    private DataFileType detectFileType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) return DataFileType.CSV;
        if (lower.endsWith(".xlsx")) return DataFileType.XLSX;
        throw new InvalidRequestException("仅支持 .csv 和 .xlsx 文件");
    }

    private void validateContent(byte[] content, DataFileType fileType, String contentType) {
        if (fileType == DataFileType.XLSX) {
            boolean zipSignature = content.length >= 4
                    && content[0] == 'P' && content[1] == 'K'
                    && content[2] == 3 && content[3] == 4;
            if (!zipSignature) throw new InvalidRequestException("XLSX 文件签名不正确");
            if (contentType != null && !contentType.isBlank()
                    && !contentType.contains("spreadsheetml")
                    && !contentType.contains("zip")
                    && !contentType.equals("application/octet-stream")) {
                throw new InvalidRequestException("XLSX 的 MIME 类型不受支持");
            }
            return;
        }

        for (byte value : content) {
            if (value == 0) throw new InvalidRequestException("CSV 不能包含二进制空字节");
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException exception) {
            throw new InvalidRequestException("CSV 必须使用 UTF-8 编码", exception);
        }
        if (contentType != null && !contentType.isBlank()
                && !contentType.contains("csv")
                && !contentType.startsWith("text/")
                && !contentType.equals("application/vnd.ms-excel")
                && !contentType.equals("application/octet-stream")) {
            throw new InvalidRequestException("CSV 的 MIME 类型不受支持");
        }
    }

    private Path resolveStoragePath(String storedFileName) {
        Path path = storageRoot.resolve(storedFileName).normalize();
        if (!path.startsWith(storageRoot)) {
            throw new InvalidRequestException("文件存储路径无效");
        }
        return path;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化数据质量任务", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取数据质量任务", exception);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 上传失败的临时文件由后续清理任务处理。
        }
    }
}
