import { apiClient, type ApiEnvelope } from './client'

export type DataFileType = 'CSV' | 'XLSX'
export type DataQualityStatus = 'PARSED' | 'ANALYZED' | 'FAILED'
export type DataRuleCode = 'REQUIRED' | 'DUPLICATE_KEY' | 'TYPE_MISMATCH' | 'NUM_RANGE'
export type ExpectedDataType = 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'DATE'

export interface DataQualityIssue {
  id: string
  rowNumber: number
  columnName: string
  ruleCode: DataRuleCode
  originalValue?: string
  severity: 'ERROR' | 'WARNING'
  description: string
  createdAt: string
}

export interface DataQualityTask {
  id: string
  projectId: string
  fileName: string
  fileType: DataFileType
  fileSize: number
  sheetName?: string
  rowCount: number
  columnCount: number
  emptyCellCount: number
  emptyCellRate: number
  status: DataQualityStatus
  headers: string[]
  previewRows: Record<string, string>[]
  summary: {
    issueCount?: number
    byRule?: Record<string, number>
    byColumn?: Record<string, number>
    duplicateKeyColumns?: string[]
    columns?: ColumnQualityRule[]
    analyzedAt?: string
  }
  issues: DataQualityIssue[]
  createdAt: string
  updatedAt: string
}

export interface ColumnQualityRule {
  columnName: string
  expectedType: ExpectedDataType
  required: boolean
  min?: number
  max?: number
}

export interface ColumnCleaningRule {
  columnName: string
  trimWhitespace: boolean
  emptyReplacement?: string
}

export interface CleanDataRequest {
  columns: ColumnCleaningRule[]
  removeDuplicates: boolean
  duplicateKeyColumns: string[]
}

export interface CleaningChange {
  rowNumber: number
  columnName: string
  originalValue: string
  cleanedValue: string
  reason: string
}

export interface CleaningPreview {
  taskId: string
  originalRowCount: number
  outputRowCount: number
  changedCellCount: number
  removedDuplicateCount: number
  removedDuplicateRowNumbers: number[]
  headers: string[]
  previewRows: Record<string, string>[]
  changes: CleaningChange[]
}

export type DataExportFormat = 'CSV' | 'XLSX'
export type DataExportDataset = 'ORIGINAL' | 'CLEANED'

export async function uploadDataFile(
  projectId: string,
  file: File,
  sheetName?: string,
): Promise<DataQualityTask> {
  const form = new FormData()
  form.append('file', file)
  const response = await apiClient.post<ApiEnvelope<DataQualityTask>>('/data-quality/tasks', form, {
    params: { projectId, sheetName: sheetName || undefined },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30_000,
  })
  return response.data.data
}

export async function listDataQualityTasks(projectId: string): Promise<DataQualityTask[]> {
  const response = await apiClient.get<ApiEnvelope<DataQualityTask[]>>('/data-quality/tasks', {
    params: { projectId },
  })
  return response.data.data
}

export async function getDataQualityTask(id: string): Promise<DataQualityTask> {
  const response = await apiClient.get<ApiEnvelope<DataQualityTask>>(`/data-quality/tasks/${id}`)
  return response.data.data
}

export async function analyzeDataQuality(
  id: string,
  columns: ColumnQualityRule[],
  duplicateKeyColumns: string[],
): Promise<DataQualityTask> {
  const response = await apiClient.post<ApiEnvelope<DataQualityTask>>(
    `/data-quality/tasks/${id}/analyze`,
    { columns, duplicateKeyColumns },
    { timeout: 30_000 },
  )
  return response.data.data
}

export async function previewDataCleaning(
  id: string,
  request: CleanDataRequest,
): Promise<CleaningPreview> {
  const response = await apiClient.post<ApiEnvelope<CleaningPreview>>(
    `/data-quality/tasks/${id}/clean-preview`,
    request,
  )
  return response.data.data
}

export async function downloadDataExport(
  id: string,
  format: DataExportFormat,
  dataset: DataExportDataset,
  request: CleanDataRequest,
  originalFileName: string,
): Promise<void> {
  const response = await apiClient.post<Blob>(
    `/data-quality/tasks/${id}/export`,
    dataset === 'CLEANED' ? request : {},
    { params: { format, dataset }, responseType: 'blob', timeout: 30_000 },
  )
  const baseName = originalFileName.replace(/\.[^.]+$/, '')
  const fileName = `${baseName}-${dataset === 'CLEANED' ? 'cleaned' : 'original'}.${format.toLowerCase()}`
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}
