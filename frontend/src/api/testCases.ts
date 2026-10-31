import { apiClient, type ApiEnvelope } from './client'

export type ScenarioType = 'POSITIVE' | 'NEGATIVE' | 'BOUNDARY'
export type CasePriority = 'P0' | 'P1' | 'P2' | 'P3'
export type CaseSource = 'MANUAL' | 'RULE' | 'AGENT'
export type ReviewStatus = 'DRAFT' | 'REVIEWED' | 'DISABLED'
export type AssertionType =
  | 'STATUS_CODE'
  | 'JSON_PATH_EXISTS'
  | 'JSON_VALUE_EQUALS'
  | 'JSON_TYPE'
  | 'NUMBER_RANGE'
  | 'RESPONSE_TIME'
export type JsonValueType = 'STRING' | 'NUMBER' | 'INTEGER' | 'BOOLEAN' | 'OBJECT' | 'ARRAY' | 'NULL'

export interface TestRequestConfig {
  pathParams: Record<string, unknown>
  queryParams: Record<string, unknown>
  headers: Record<string, string>
  body: unknown | null
  timeoutMillis: number
}

export interface AssertionConfig {
  type: AssertionType
  path?: string
  expected?: unknown
  expectedType?: JsonValueType
  min?: number
  max?: number
  maxMillis?: number
}

export interface TestCase {
  id: string
  projectId: string
  apiDefinitionId: string
  name: string
  scenarioType: ScenarioType
  request: TestRequestConfig
  assertions: AssertionConfig[]
  priority: CasePriority
  source: CaseSource
  reviewStatus: ReviewStatus
  version: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface SaveTestCasePayload {
  projectId?: string
  apiDefinitionId: string
  name: string
  scenarioType: ScenarioType
  request: TestRequestConfig
  assertions: AssertionConfig[]
  priority: CasePriority
}

export interface TestCaseFilters {
  apiDefinitionId?: string
  reviewStatus?: ReviewStatus
  enabled?: boolean
}

export async function listTestCases(
  projectId: string,
  filters: TestCaseFilters = {},
): Promise<TestCase[]> {
  const response = await apiClient.get<ApiEnvelope<TestCase[]>>('/test-cases', {
    params: { projectId, ...filters },
  })
  return response.data.data
}

export async function createTestCase(payload: SaveTestCasePayload): Promise<TestCase> {
  const response = await apiClient.post<ApiEnvelope<TestCase>>('/test-cases', payload)
  return response.data.data
}

export async function updateTestCase(
  id: string,
  payload: SaveTestCasePayload,
): Promise<TestCase> {
  const { projectId: _projectId, ...body } = payload
  const response = await apiClient.put<ApiEnvelope<TestCase>>(`/test-cases/${id}`, body)
  return response.data.data
}

export async function reviewTestCase(id: string, approvalToken: string): Promise<TestCase> {
  const response = await apiClient.post<ApiEnvelope<TestCase>>(
    `/test-cases/${id}/review`,
    { approved: true },
    { headers: { 'X-Local-Approval-Token': approvalToken } },
  )
  return response.data.data
}

export async function setTestCaseEnabled(id: string, enabled: boolean): Promise<TestCase> {
  const response = await apiClient.patch<ApiEnvelope<TestCase>>(`/test-cases/${id}/enabled`, {
    enabled,
  })
  return response.data.data
}

export async function deleteTestCase(id: string): Promise<void> {
  await apiClient.delete(`/test-cases/${id}`)
}
