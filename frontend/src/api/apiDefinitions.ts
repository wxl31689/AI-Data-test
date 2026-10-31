import { apiClient, type ApiEnvelope } from './client'

export interface ApiDefinition {
  id: string
  projectId: string
  name: string
  path: string
  method: string
  summary?: string
  baseUrl?: string
  parametersJson?: string
  requestSchema?: string
  responseSchema?: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface OpenApiImportResult {
  documentTitle: string
  documentVersion: string
  importedCount: number
  updatedCount: number
  warnings: string[]
  definitions: ApiDefinition[]
}

export async function listApiDefinitions(projectId: string): Promise<ApiDefinition[]> {
  const response = await apiClient.get<ApiEnvelope<ApiDefinition[]>>('/api-definitions', {
    params: { projectId },
  })
  return response.data.data
}

export async function importOpenApi(projectId: string, file: File): Promise<OpenApiImportResult> {
  const form = new FormData()
  form.append('file', file)

  const response = await apiClient.post<ApiEnvelope<OpenApiImportResult>>(
    '/api-definitions/import',
    form,
    {
      params: { projectId },
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 30_000,
    },
  )
  return response.data.data
}
