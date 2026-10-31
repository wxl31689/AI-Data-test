<script setup lang="ts">
import { Check, Cpu, MagicStick, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { isAxiosError } from 'axios'
import {
  acceptAgentTestCase,
  analyzeAgentFailure,
  generateAgentTestCases,
  executeAgentTestRun,
  getAgentExecutionStatus,
  getAgentStatus,
  listAgentToolAudits,
  type AgentExecution,
  type AgentToolAudit,
  type AgentFailureAnalysis,
  type AgentGenerationResponse,
  type AgentStatus,
} from '../api/agent'
import { listApiDefinitions, type ApiDefinition } from '../api/apiDefinitions'
import { listProjects, type Project } from '../api/projects'
import { listTestRuns, type TestRun } from '../api/testRuns'
import { listTestCases, reviewTestCase, type TestCase } from '../api/testCases'

const router = useRouter()

const status = ref<AgentStatus | null>(null)
const projects = ref<Project[]>([])
const definitions = ref<ApiDefinition[]>([])
const runs = ref<TestRun[]>([])
const testCases = ref<TestCase[]>([])
const audits = ref<AgentToolAudit[]>([])
const projectId = ref('')
const apiDefinitionId = ref('')
const runId = ref('')
const caseFocus = ref('覆盖正常路径、边界值和异常响应')
const analysisFocus = ref('定位预期失败的断言差异，并给出可执行建议')
const count = ref(2)
const generating = ref(false)
const analyzing = ref(false)
const accepting = ref<string[]>([])
const generation = ref<AgentGenerationResponse | null>(null)
const analysis = ref<AgentFailureAnalysis | null>(null)
const selectedCaseIds = ref<string[]>([])
const executing = ref(false)
const execution = ref<AgentExecution | null>(null)
const pendingApprovalId = ref('')
const pendingExecutionPayload = ref<Parameters<typeof executeAgentTestRun>[0] | null>(null)

const failedRuns = computed(() => runs.value.filter((run) => run.status !== 'RUNNING' && run.failCount + run.errorCount > 0))
const available = computed(() => Boolean(status.value?.enabled && status.value?.configured))

async function initialize() {
  try {
    const [agentStatus, projectList] = await Promise.all([getAgentStatus(), listProjects()])
    status.value = agentStatus
    projects.value = projectList
    if (projectList.length) projectId.value = projectList[0].id
  } catch {
    ElMessage.error('Agent 状态或项目加载失败')
  }
}

async function loadProjectContext() {
  generation.value = null
  analysis.value = null
  apiDefinitionId.value = ''
  runId.value = ''
  if (!projectId.value) return
  try {
    const [apiList, runList, caseList, auditList] = await Promise.all([
      listApiDefinitions(projectId.value),
      listTestRuns(projectId.value),
      listTestCases(projectId.value),
      listAgentToolAudits(projectId.value),
    ])
    definitions.value = apiList.filter((item) => item.enabled)
    runs.value = runList
    testCases.value = caseList
    audits.value = auditList
    selectedCaseIds.value = caseList.filter((item) => item.enabled && item.reviewStatus === 'REVIEWED').map((item) => item.id)
    if (definitions.value.length) apiDefinitionId.value = definitions.value[0].id
    const firstFailed = runList.find((run) => run.status !== 'RUNNING' && run.failCount + run.errorCount > 0)
    if (firstFailed) runId.value = firstFailed.id
  } catch {
    ElMessage.error('项目上下文加载失败')
  }
}

async function generate() {
  if (!projectId.value || !apiDefinitionId.value) return
  generating.value = true
  try {
    generation.value = await generateAgentTestCases({
      projectId: projectId.value,
      apiDefinitionId: apiDefinitionId.value,
      count: count.value,
      focus: caseFocus.value,
    })
    ElMessage.success('结构化候选用例已生成，等待人工接收')
  } catch {
    ElMessage.error('Agent 生成失败，请检查模型配置或后端日志')
  } finally {
    generating.value = false
  }
}

async function accept(index: number) {
  if (!generation.value) return
  const candidate = generation.value.candidates[index]
  accepting.value.push(candidate.name)
  try {
    const created = await acceptAgentTestCase({
      projectId: projectId.value,
      apiDefinitionId: apiDefinitionId.value,
      candidate,
    })
    ElMessage.success(`已保存为 ${created.source} / ${created.reviewStatus}，审核后方可执行`)
    testCases.value = await listTestCases(projectId.value)
  } catch {
    ElMessage.error('候选用例保存失败')
  } finally {
    accepting.value = accepting.value.filter((name) => name !== candidate.name)
  }
}

async function reviewCase(testCase: TestCase) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请确认已人工检查“${testCase.name}”的请求和断言，并输入本地审批令牌：`,
      '人工审核',
      { inputType: 'password', inputPlaceholder: 'LOCAL_APPROVAL_TOKEN', confirmButtonText: '审核通过' },
    )
    await reviewTestCase(testCase.id, value)
    testCases.value = await listTestCases(projectId.value)
    ElMessage.success('服务端已记录 REVIEWED 状态')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('审核失败或没有本地审批权限')
  }
}

async function executeApprovedCases() {
  if (pendingApprovalId.value) {
    await refreshPendingExecution()
    return
  }
  if (pendingExecutionPayload.value && pendingExecutionPayload.value.projectId !== projectId.value) {
    ElMessage.warning('另一个项目的执行请求尚未确认，请切回原项目查询或重试原幂等请求')
    return
  }
  const selected = testCases.value.filter((item) => selectedCaseIds.value.includes(item.id))
  if (!selected.length || selected.some((item) => !item.enabled || item.reviewStatus !== 'REVIEWED')) {
    ElMessage.warning('只能选择已启用且已人工审核的用例')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt(
      `将授权 Agent 通过现有测试引擎创建 ${selected.length} 条用例的异步任务。请输入本地审批令牌：`,
      '明确执行授权',
      { inputType: 'password', inputPlaceholder: 'LOCAL_APPROVAL_TOKEN', confirmButtonText: '授权并执行' },
    )
    executing.value = true
    const payload = pendingExecutionPayload.value ?? {
      projectId: projectId.value,
      caseIds: selected.map((item) => item.id),
      runName: `Agent 受控执行 - ${new Date().toLocaleString('zh-CN')}`,
      clientRequestId: crypto.randomUUID(),
      maxWaitMillis: 5000,
    }
    pendingExecutionPayload.value = payload
    execution.value = await executeAgentTestRun(payload, value)
    pendingExecutionPayload.value = null
    const startedAt = Date.now()
    while (execution.value.run.status === 'RUNNING' && Date.now() - startedAt < 60_000) {
      await new Promise((resolve) => window.setTimeout(resolve, 700))
      const status = await getAgentExecutionStatus(execution.value.approvalId)
      if (status.run) execution.value = { ...execution.value, run: status.run }
    }
    audits.value = await listAgentToolAudits(projectId.value)
    runs.value = await listTestRuns(projectId.value)
    if (execution.value.run.failCount + execution.value.run.errorCount > 0) runId.value = execution.value.run.id
    if (execution.value.run.status === 'RUNNING') ElMessage.warning('任务仍在运行，可到测试执行页面继续查看')
    else if (execution.value.run.status === 'FAILED') ElMessage.error('测试任务失败，请查看审计与运行证据')
    else ElMessage.success('确定性测试执行完成')
  } catch (error) {
    if (isAxiosError(error) && error.response?.data.code === 'AGENT_TOOL_TIMEOUT') {
      pendingApprovalId.value = error.response.data.data.approvalId
      ElMessage.warning('工具等待超时，创建结果待确认，请查询审批状态，不要重复授权')
    } else if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(isAxiosError(error) ? error.response?.data.message || '受控执行失败' : '受控执行失败')
      if (isAxiosError(error) && error.response && error.response.status < 500) pendingExecutionPayload.value = null
    }
  } finally {
    executing.value = false
    audits.value = await listAgentToolAudits(projectId.value).catch(() => audits.value)
  }
}

async function refreshPendingExecution() {
  if (!pendingApprovalId.value) return
  try {
    const state = await getAgentExecutionStatus(pendingApprovalId.value)
    if (state.run) {
      execution.value = { agentTaskId: state.agentTaskId, approvalId: state.approvalId,
        auditId: '', idempotentReplay: true, modelAcknowledgement: '已核对持久化任务', run: state.run }
      pendingApprovalId.value = ''
      pendingExecutionPayload.value = null
    } else ElMessage.warning(`审批状态：${state.approvalStatus}；尚未确认创建任务，请勿重复授权`)
    audits.value = await listAgentToolAudits(projectId.value)
  } catch { ElMessage.error('审批状态查询失败') }
}

async function analyze() {
  if (!runId.value) return
  analyzing.value = true
  try {
    analysis.value = await analyzeAgentFailure(runId.value, analysisFocus.value)
    audits.value = await listAgentToolAudits(projectId.value)
    ElMessage.success('已依据持久化测试证据完成失败分析')
  } catch {
    ElMessage.error('失败分析未完成，请检查运行证据或模型配置')
  } finally {
    analyzing.value = false
  }
}

watch(projectId, loadProjectContext)
onMounted(initialize)
</script>

<template>
  <div class="agent-page">
    <section class="page-intro">
      <div>
        <span class="section-label">AI Agent 闭环</span>
        <h2>让模型提出候选方案，让测试引擎保留最终判定权</h2>
        <p>结构化生成、人工接收、只读工具调用和真实失败证据分析均可审计；Agent 不能绕过断言引擎修改结果。</p>
      </div>
      <el-tag :type="available ? 'success' : 'warning'" size="large" effect="light">
        {{ available ? `${status?.model} · 密钥已提供` : '等待模型配置' }}
      </el-tag>
    </section>

    <el-alert v-if="status" :title="`${status.verdictPolicy}；“密钥已提供”不代表远端认证已通过，以实际生成/分析结果为准。`" type="info" :closable="false" show-icon />

    <section class="agent-status-grid">
      <article class="panel agent-stat"><Cpu /><div><span>模型提供方</span><strong>{{ status?.provider ?? '--' }}</strong></div></article>
      <article class="panel agent-stat"><MagicStick /><div><span>受控能力</span><strong>{{ status?.capabilities.length ?? 0 }} 项</strong></div></article>
      <article class="panel agent-stat"><Check /><div><span>判定边界</span><strong>确定性引擎</strong></div></article>
    </section>

    <section class="panel agent-context">
      <div><label>项目</label><el-select v-model="projectId" placeholder="选择项目"><el-option v-for="item in projects" :key="item.id" :label="item.name" :value="item.id" /></el-select></div>
      <div><label>接口</label><el-select v-model="apiDefinitionId" placeholder="选择启用接口"><el-option v-for="item in definitions" :key="item.id" :label="`${item.method} ${item.path}`" :value="item.id" /></el-select></div>
    </section>

    <section class="agent-columns">
      <article class="panel agent-workbench">
        <div class="panel-head"><div><span class="section-label">能力 01</span><h3>生成结构化测试用例</h3></div><el-icon><MagicStick /></el-icon></div>
        <el-form label-position="top">
          <el-form-item label="生成重点"><el-input v-model="caseFocus" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
          <el-form-item label="候选数量"><el-input-number v-model="count" :min="1" :max="5" /></el-form-item>
          <el-button type="primary" :icon="MagicStick" :loading="generating" :disabled="!available || !apiDefinitionId" @click="generate">生成候选用例</el-button>
        </el-form>

        <div v-if="generation" class="result-stack">
          <el-alert :title="generation.reviewNotice" type="warning" :closable="false" />
          <article v-for="(candidate, index) in generation.candidates" :key="`${candidate.name}-${index}`" class="candidate-card">
            <div class="candidate-head"><div><strong>{{ candidate.name }}</strong><p>{{ candidate.rationale }}</p></div><el-tag>{{ candidate.priority }} · {{ candidate.scenarioType }}</el-tag></div>
            <div class="chips"><span v-for="item in candidate.assertions" :key="`${item.type}-${item.path}`">{{ item.type }}{{ item.path ? ` · ${item.path}` : '' }}</span></div>
            <p class="risk">风险：{{ candidate.risk || '无额外说明' }}</p>
            <details><summary>检查候选请求与断言</summary><pre>{{ JSON.stringify({ request: candidate.request, assertions: candidate.assertions }, null, 2) }}</pre></details>
            <el-button type="success" plain :loading="accepting.includes(candidate.name)" @click="accept(index)">人工接收为草稿</el-button>
          </article>
        </div>
      </article>

      <article class="panel agent-workbench">
        <div class="panel-head"><div><span class="section-label">能力 02</span><h3>分析真实失败证据</h3></div><el-icon><Search /></el-icon></div>
        <el-form label-position="top">
          <el-form-item label="包含 FAIL / ERROR 的任务"><el-select v-model="runId" placeholder="选择测试任务"><el-option v-for="run in failedRuns" :key="run.id" :label="`${run.runName} · ${run.failCount} FAIL / ${run.errorCount} ERROR`" :value="run.id" /></el-select></el-form-item>
          <el-form-item label="分析重点"><el-input v-model="analysisFocus" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
          <el-button type="primary" :icon="Search" :loading="analyzing" :disabled="!available || !runId" @click="analyze">调用只读证据工具并分析</el-button>
        </el-form>

        <div v-if="analysis" class="result-stack">
          <div class="verdict-row"><span>引擎统计</span><strong>{{ analysis.deterministicSummary.passCount }} PASS / {{ analysis.deterministicSummary.failCount }} FAIL / {{ analysis.deterministicSummary.errorCount }} ERROR</strong></div>
          <p class="analysis-summary">{{ analysis.summary }}</p>
          <article v-for="cause in analysis.rootCauses" :key="cause.testResultId" class="cause-card">
            <div><el-tag type="danger">{{ cause.deterministicStatus }}</el-tag><el-tag type="info">{{ cause.category }}</el-tag><el-tag>{{ cause.confidence }}</el-tag></div>
            <strong>{{ cause.caseName }}</strong><p>{{ cause.explanation }}</p><small>证据：{{ cause.evidence }}</small><p class="action">建议：{{ cause.suggestedAction }}</p>
          </article>
          <div v-for="call in analysis.toolCalls" :key="call.callId" class="tool-audit"><Check /> <span><strong>{{ call.name }}</strong><br />{{ call.policy }}</span></div>
        </div>
      </article>
    </section>

    <section class="panel controlled-execution">
      <div class="panel-head">
        <div><span class="section-label">能力 03</span><h3>人工审核 → Agent 受控执行 → 持久化审计</h3></div>
        <el-button type="primary" :loading="executing" :disabled="!available || !selectedCaseIds.length || Boolean(pendingApprovalId)" @click="executeApprovedCases">明确授权并执行</el-button>
      </div>
      <el-alert title="本地审批令牌只用于演示环境权限边界；Agent 不能审核用例，也不能传入主机、SQL、命令或文件路径。" type="warning" :closable="false" />
      <div v-if="pendingApprovalId"><el-tag type="warning">创建结果待确认 · {{ pendingApprovalId }}</el-tag><el-button @click="refreshPendingExecution">查询审批和任务</el-button></div>
      <div class="execution-grid">
        <div>
          <h4>候选与已保存用例</h4>
          <el-checkbox-group v-model="selectedCaseIds" class="case-checks">
            <div v-for="item in testCases" :key="item.id" class="case-check-row">
              <el-checkbox :value="item.id" :disabled="!item.enabled || item.reviewStatus !== 'REVIEWED'">{{ item.name }}</el-checkbox>
              <el-tag :type="item.reviewStatus === 'REVIEWED' ? 'success' : 'info'">{{ item.reviewStatus }}</el-tag>
              <el-button v-if="item.reviewStatus === 'DRAFT'" link type="warning" @click="reviewCase(item)">人工审核</el-button>
              <el-popover placement="top" :width="520" trigger="click"><template #reference><el-button link>查看配置</el-button></template><pre>{{ JSON.stringify({ request: item.request, assertions: item.assertions }, null, 2) }}</pre></el-popover>
            </div>
          </el-checkbox-group>
        </div>
        <div>
          <h4>最近执行</h4>
          <el-empty v-if="!execution" description="尚未创建受控执行任务" :image-size="70" />
          <div v-else class="execution-result">
            <p><strong>{{ execution.run.runName }}</strong></p>
            <el-tag :type="execution.run.status === 'COMPLETED' ? 'success' : execution.run.status === 'FAILED' ? 'danger' : 'warning'">{{ execution.run.status }}</el-tag>
            <p>{{ execution.run.processedCount }}/{{ execution.run.totalCount }} · {{ execution.run.passCount }} PASS · {{ execution.run.failCount }} FAIL · {{ execution.run.errorCount }} ERROR</p>
            <small>{{ execution.modelAcknowledgement }}</small>
            <div><el-button link type="primary" @click="router.push({ path: '/runs', query: { projectId, runId: execution.run.id } })">查看测试结果</el-button><el-button link type="success" @click="router.push({ path: '/reports', query: { projectId, runId: execution.run.id } })">查看报告</el-button></div>
          </div>
        </div>
      </div>
      <h4>工具调用审计</h4>
      <el-table :data="audits" size="small" empty-text="暂无审计记录">
        <el-table-column prop="toolName" label="工具" min-width="160" />
        <el-table-column prop="status" label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 'SUCCESS' ? 'success' : scope.row.status === 'STARTED' ? 'warning' : 'danger'">{{ scope.row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="runId" label="测试任务" min-width="190" show-overflow-tooltip />
        <el-table-column prop="approvalId" label="关联审批" min-width="180" show-overflow-tooltip />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column label="结果 / 错误" min-width="240"><template #default="scope">{{ scope.row.resultSummary || scope.row.errorType || '--' }}</template></el-table-column>
        <el-table-column prop="errorType" label="错误类型" min-width="150" />
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.agent-status-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin: 20px 0; }
.agent-stat { display: flex; align-items: center; gap: 14px; padding: 20px; color: var(--app-accent); }
.agent-stat > svg { width: 28px; }
.agent-stat span, .agent-stat strong { display: block; }
.agent-stat span { color: var(--app-text-muted); font-size: 12px; }
.agent-stat strong { margin-top: 5px; color: var(--app-text-primary); }
.agent-context { display: grid; grid-template-columns: 1fr 1.4fr; gap: 16px; margin-bottom: 20px; padding: 20px; }
.agent-context > div { display: grid; gap: 7px; }
.agent-context label { color: var(--app-text-secondary); font-size: 12px; font-weight: 700; }
.agent-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.agent-workbench { padding: 24px; }
.agent-workbench .panel-head { margin-bottom: 22px; }
.agent-workbench .panel-head > .el-icon { color: var(--app-accent); font-size: 30px; }
.result-stack { display: grid; gap: 12px; margin-top: 22px; }
.candidate-card, .cause-card { padding: 16px; border: 1px solid var(--app-border); border-radius: 14px; background: var(--app-surface-subtle); }
.candidate-head { display: flex; justify-content: space-between; gap: 12px; }
.candidate-head p, .cause-card p { margin: 6px 0; color: var(--app-text-secondary); font-size: 13px; line-height: 1.6; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; margin: 12px 0; }
.chips span { padding: 5px 8px; border-radius: 8px; color: var(--app-accent-strong); background: var(--app-accent-soft); font-size: 11px; }
.risk, .cause-card small { color: #8b6b2d !important; font-size: 12px !important; }
.verdict-row { display: flex; justify-content: space-between; padding: 14px; border-radius: 12px; background: var(--app-accent-soft); color: var(--app-accent-strong); }
.analysis-summary { margin: 0; color: var(--app-text-secondary); line-height: 1.7; }
.cause-card .el-tag { margin-right: 5px; }
.cause-card > strong { display: block; margin-top: 10px; }
.cause-card .action { color: var(--app-accent-strong); font-weight: 600; }
.tool-audit { display: flex; gap: 10px; padding: 14px; border-left: 3px solid var(--app-accent); background: var(--app-surface-subtle); color: var(--app-text-secondary); font-size: 12px; }
.tool-audit > svg { width: 19px; color: var(--app-accent); }
.controlled-execution { margin-top: 20px; padding: 24px; }
.execution-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin: 18px 0; }
.case-checks { display: grid; gap: 8px; }
.case-check-row { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border: 1px solid var(--app-border); border-radius: 9px; }
.case-check-row .el-checkbox { flex: 1; }
.execution-result { padding: 15px; border-radius: 12px; background: var(--app-surface-subtle); color: var(--app-text-secondary); }
.execution-result p { margin: 8px 0; }
pre { max-height: 280px; overflow: auto; white-space: pre-wrap; word-break: break-all; font-size: 12px; }
@media (max-width: 1050px) { .agent-columns { grid-template-columns: 1fr; } }
@media (max-width: 700px) { .agent-status-grid, .agent-context, .execution-grid { grid-template-columns: 1fr; } }
</style>
