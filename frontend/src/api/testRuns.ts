import { apiClient, type ApiEnvelope } from './client'

export type RunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED'
export type ResultStatus = 'PASS' | 'FAIL' | 'ERROR' | 'SKIPPED'

export interface AssertionResult {
  id: string
  assertionType: string
  expectedValue?: string
  actualValue?: string
  passed: boolean
  message: string
  createdAt: string
}

export interface TestResult {
  id: string
  testCaseId: string
  caseName: string
  requestSummary?: string
  responseSummary?: string
  statusCode?: number
  durationMs?: number
  resultStatus: ResultStatus
  errorMessage?: string
  createdAt: string
  assertions: AssertionResult[]
}

export interface TestRun {
  id: string
  projectId: string
  runName: string
  triggerType: 'MANUAL' | 'RERUN' | 'AGENT'
  status: RunStatus
  totalCount: number
  processedCount: number
  passCount: number
  failCount: number
  errorCount: number
  skippedCount: number
  startedAt: string
  finishedAt?: string
  parentRunId?: string
  results: TestResult[]
}

export interface TestRunReport {
  run: TestRun
  passRate: number
  totalDurationMs: number
  averageDurationMs: number
  p95DurationMs: number
  maxDurationMs: number
  resultDistribution: Record<ResultStatus, number>
  failures: TestResult[]
  generatedAt: string
}

export async function executeTestCases(
  projectId: string,
  caseIds: string[],
  runName?: string,
  clientRequestId = crypto.randomUUID(),
): Promise<TestRun> {
  const response = await apiClient.post<ApiEnvelope<TestRun>>('/test-runs', {
    projectId,
    caseIds,
    runName,
    clientRequestId,
  })
  return response.data.data
}

export async function listTestRuns(projectId: string): Promise<TestRun[]> {
  const response = await apiClient.get<ApiEnvelope<TestRun[]>>('/test-runs', {
    params: { projectId },
  })
  return response.data.data
}

export async function getTestRun(id: string): Promise<TestRun> {
  const response = await apiClient.get<ApiEnvelope<TestRun>>(`/test-runs/${id}`)
  return response.data.data
}

export async function rerunFailed(id: string): Promise<TestRun> {
  const response = await apiClient.post<ApiEnvelope<TestRun>>(`/test-runs/${id}/rerun-failed`, {
    clientRequestId: crypto.randomUUID(),
  })
  return response.data.data
}

export async function waitForTestRun(
  id: string,
  onProgress?: (run: TestRun) => void,
  timeoutMillis = 60_000,
): Promise<TestRun> {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMillis) {
    const run = await getTestRun(id)
    onProgress?.(run)
    if (run.status !== 'RUNNING') return run
    await new Promise((resolve) => window.setTimeout(resolve, 500))
  }
  throw new Error('等待测试任务超时')
}

export async function getTestRunReport(id: string): Promise<TestRunReport> {
  const response = await apiClient.get<ApiEnvelope<TestRunReport>>(`/test-runs/${id}/report`)
  return response.data.data
}

export async function downloadTestRunReport(id: string, runName: string): Promise<void> {
  const response = await apiClient.get<Blob>(`/test-runs/${id}/report/export`, {
    responseType: 'blob',
  })
  const safeName = runName.replace(/[\\/:*?"<>|]/g, '-').trim() || 'test-run-report'
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${safeName}.html`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}
