import { apiClient, type ApiEnvelope } from './client'
import type { AssertionConfig, CasePriority, ScenarioType, TestCase, TestRequestConfig } from './testCases'
import type { ResultStatus, RunStatus, TestRun } from './testRuns'

export interface AgentStatus {
  enabled: boolean
  configured: boolean
  provider: string
  model: string
  capabilities: string[]
  verdictPolicy: string
}

export interface AgentTestCaseCandidate {
  name: string
  scenarioType: ScenarioType
  priority: CasePriority
  request: TestRequestConfig
  assertions: AssertionConfig[]
  rationale: string
  risk: string
}

export interface AgentGenerationResponse {
  projectId: string
  apiDefinitionId: string
  model: string
  candidates: AgentTestCaseCandidate[]
  reviewNotice: string
  generatedAt: string
}

export interface AgentRootCause {
  testResultId: string
  caseName: string
  deterministicStatus: ResultStatus
  category: string
  evidence: string
  explanation: string
  confidence: 'HIGH' | 'MEDIUM' | 'LOW'
  suggestedAction: string
}

export interface AgentFailureAnalysis {
  runId: string
  model: string
  deterministicSummary: {
    status: RunStatus
    totalCount: number
    processedCount: number
    passCount: number
    failCount: number
    errorCount: number
    skippedCount: number
  }
  summary: string
  rootCauses: AgentRootCause[]
  recommendations: string[]
  limitations: string[]
  toolCalls: Array<{ name: string; callId: string; runId: string; approved: boolean; policy: string }>
  verdictPolicy: string
  generatedAt: string
}

export interface AgentExecution {
  agentTaskId: string
  approvalId: string
  auditId: string
  idempotentReplay: boolean
  modelAcknowledgement: string
  run: TestRun
}

export interface AgentExecutionStatus {
  agentTaskId: string
  approvalId: string
  projectId: string
  approvalStatus: 'APPROVED' | 'EXECUTED' | 'FAILED' | 'TIMED_OUT'
  approvedAt: string
  expiresAt: string
  run?: TestRun
}

export interface AgentToolAudit {
  id: string
  agentTaskId: string
  toolName: string
  projectId?: string
  runId?: string
  approvalId?: string
  inputSummary: string
  startedAt: string
  finishedAt?: string
  durationMs?: number
  status: 'STARTED' | 'SUCCESS' | 'FAILED' | 'TIMED_OUT'
  resultSummary?: string
  errorType?: string
}

export async function getAgentStatus(): Promise<AgentStatus> {
  const response = await apiClient.get<ApiEnvelope<AgentStatus>>('/agent/status')
  return response.data.data
}

export async function generateAgentTestCases(payload: {
  projectId: string
  apiDefinitionId: string
  count: number
  focus?: string
}): Promise<AgentGenerationResponse> {
  const response = await apiClient.post<ApiEnvelope<AgentGenerationResponse>>(
    '/agent/test-cases/generate', payload, { timeout: 45_000 },
  )
  return response.data.data
}

export async function acceptAgentTestCase(payload: {
  projectId: string
  apiDefinitionId: string
  candidate: AgentTestCaseCandidate
}): Promise<TestCase> {
  const response = await apiClient.post<ApiEnvelope<TestCase>>('/agent/test-cases/accept', payload)
  return response.data.data
}

export async function analyzeAgentFailure(runId: string, focus?: string): Promise<AgentFailureAnalysis> {
  const response = await apiClient.post<ApiEnvelope<AgentFailureAnalysis>>(
    `/agent/runs/${runId}/analyze`, { focus: focus || null }, { timeout: 75_000 },
  )
  return response.data.data
}

export async function executeAgentTestRun(payload: {
  projectId: string
  caseIds: string[]
  runName: string
  clientRequestId: string
  maxWaitMillis?: number
}, approvalToken: string): Promise<AgentExecution> {
  const response = await apiClient.post<ApiEnvelope<AgentExecution>>(
    '/agent/test-runs/execute', payload,
    { headers: { 'X-Local-Approval-Token': approvalToken }, timeout: 90_000 },
  )
  return response.data.data
}

export async function getAgentExecutionStatus(approvalId: string): Promise<AgentExecutionStatus> {
  const response = await apiClient.get<ApiEnvelope<AgentExecutionStatus>>(`/agent/executions/${approvalId}`)
  return response.data.data
}

export async function listAgentToolAudits(projectId?: string): Promise<AgentToolAudit[]> {
  const response = await apiClient.get<ApiEnvelope<AgentToolAudit[]>>('/agent/audits', {
    params: { projectId, limit: 50 },
  })
  return response.data.data
}
