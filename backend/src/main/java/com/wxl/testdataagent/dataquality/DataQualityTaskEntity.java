package com.wxl.testdataagent.dataquality;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_quality_task")
public class DataQualityTaskEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "stored_file_name", length = 100, nullable = false)
    private String storedFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", length = 12, nullable = false)
    private DataFileType fileType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "sheet_name", length = 160)
    private String sheetName;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "column_count", nullable = false)
    private int columnCount;

    @Column(name = "empty_cell_count", nullable = false)
    private long emptyCellCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private DataQualityStatus status;

    @Column(name = "headers_json", columnDefinition = "TEXT", nullable = false)
    private String headersJson;

    @Column(name = "preview_json", columnDefinition = "TEXT", nullable = false)
    private String previewJson;

    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DataQualityTaskEntity() {
    }

    public DataQualityTaskEntity(
            String projectId,
            String fileName,
            String storedFileName,
            DataFileType fileType,
            long fileSize,
            String sheetName,
            int rowCount,
            int columnCount,
            long emptyCellCount,
            String headersJson,
            String previewJson,
            String summaryJson
    ) {
        this.id = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.fileName = fileName;
        this.storedFileName = storedFileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.sheetName = sheetName;
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.emptyCellCount = emptyCellCount;
        this.status = DataQualityStatus.PARSED;
        this.headersJson = headersJson;
        this.previewJson = previewJson;
        this.summaryJson = summaryJson;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markAnalyzed(String summaryJson) {
        this.summaryJson = summaryJson;
        this.status = DataQualityStatus.ANALYZED;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getFileName() { return fileName; }
    public String getStoredFileName() { return storedFileName; }
    public DataFileType getFileType() { return fileType; }
    public long getFileSize() { return fileSize; }
    public String getSheetName() { return sheetName; }
    public int getRowCount() { return rowCount; }
    public int getColumnCount() { return columnCount; }
    public long getEmptyCellCount() { return emptyCellCount; }
    public DataQualityStatus getStatus() { return status; }
    public String getHeadersJson() { return headersJson; }
    public String getPreviewJson() { return previewJson; }
    public String getSummaryJson() { return summaryJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
