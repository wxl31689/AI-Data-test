<script setup lang="ts">
import { Delete, Edit, Plus, Refresh, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { listApiDefinitions, type ApiDefinition } from '../api/apiDefinitions'
import { listProjects, type Project } from '../api/projects'
import {
  createTestCase,
  deleteTestCase,
  listTestCases,
  reviewTestCase,
  setTestCaseEnabled,
  updateTestCase,
  type AssertionConfig,
  type AssertionType,
  type JsonValueType,
  type ReviewStatus,
  type SaveTestCasePayload,
  type TestCase,
} from '../api/testCases'
import { executeTestCases, waitForTestRun, type TestRun } from '../api/testRuns'

interface AssertionDraft {
  type: AssertionType
  path: string
  expectedText: string
  expectedType: JsonValueType
  minText: string
  maxText: string
  maxMillis: number
}

const projects = ref<Project[]>([])
const definitions = ref<ApiDefinition[]>([])
const cases = ref<TestCase[]>([])
const selectedProjectId = ref('')
const reviewFilter = ref<ReviewStatus | ''>('')
const loading = ref(false)
const saving = ref(false)
const executing = ref(false)
const dialogVisible = ref(false)
const resultDrawerVisible = ref(false)
const editingId = ref<string | null>(null)
const selectedCases = ref<TestCase[]>([])
const currentRun = ref<TestRun | null>(null)

const form = reactive({
  name: '',
  apiDefinitionId: '',
  scenarioType: 'POSITIVE' as 'POSITIVE' | 'NEGATIVE' | 'BOUNDARY',
  priority: 'P1' as 'P0' | 'P1' | 'P2' | 'P3',
  pathParamsText: '{}',
  queryParamsText: '{}',
  headersText: '{}',
  bodyText: '',
  timeoutMillis: 5000,
  assertions: [{
    type: 'STATUS_CODE', path: '', expectedText: '200', expectedType: 'STRING',
    minText: '', maxText: '', maxMillis: 1500,
  }] as AssertionDraft[],
})

const selectedProject = computed(() =>
  projects.value.find((project) => project.id === selectedProjectId.value),
)

const canExecuteSelected = computed(
  () => selectedCases.value.length > 0
    && selectedCases.value.every((item) => item.enabled && item.reviewStatus === 'REVIEWED'),
)

async function initialize() {
  try {
    projects.value = await listProjects()
    if (projects.value.length > 0) selectedProjectId.value = projects.value[0].id
  } catch {
    ElMessage.error('无法加载项目，请确认后端服务已启动')
  }
}

async function loadProjectData() {
  if (!selectedProjectId.value) {
    definitions.value = []
    cases.value = []
    return
  }
  loading.value = true
  selectedCases.value = []
  try {
    const [apiItems, caseItems] = await Promise.all([
      listApiDefinitions(selectedProjectId.value),
      listTestCases(selectedProjectId.value, {
        reviewStatus: reviewFilter.value || undefined,
      }),
    ])
    definitions.value = apiItems
    cases.value = caseItems
  } catch {
    ElMessage.error('用例数据加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = null
  form.name = ''
  form.apiDefinitionId = definitions.value[0]?.id ?? ''
  form.scenarioType = 'POSITIVE'
  form.priority = 'P1'
  form.pathParamsText = '{}'
  form.queryParamsText = '{}'
  form.headersText = '{}'
  form.bodyText = ''
  form.timeoutMillis = 5000
  form.assertions = [{
    type: 'STATUS_CODE', path: '', expectedText: '200', expectedType: 'STRING',
    minText: '', maxText: '', maxMillis: 1500,
  }]
}

function openCreateDialog() {
  if (!selectedProjectId.value) {
    ElMessage.warning('请先创建项目')
    return
  }
  if (definitions.value.length === 0) {
    ElMessage.warning('请先在“项目与接口”中导入 OpenAPI 定义')
    return
  }
  resetForm()
  dialogVisible.value = true
}

function jsonText(value: unknown, empty = '{}') {
  if (value === null || value === undefined) return empty
  return JSON.stringify(value, null, 2)
}

function expectedText(assertion: AssertionConfig) {
  if (assertion.expected === undefined) return ''
  return typeof assertion.expected === 'string'
    ? JSON.stringify(assertion.expected)
    : JSON.stringify(assertion.expected)
}

function openEditDialog(testCase: TestCase) {
  editingId.value = testCase.id
  form.name = testCase.name
  form.apiDefinitionId = testCase.apiDefinitionId
  form.scenarioType = testCase.scenarioType
  form.priority = testCase.priority
  form.pathParamsText = jsonText(testCase.request.pathParams)
  form.queryParamsText = jsonText(testCase.request.queryParams)
  form.headersText = jsonText(testCase.request.headers)
  form.bodyText = jsonText(testCase.request.body, '')
  form.timeoutMillis = testCase.request.timeoutMillis
  form.assertions = testCase.assertions.map((assertion) => ({
    type: assertion.type,
    path: assertion.path ?? '',
    expectedText: expectedText(assertion),
    expectedType: assertion.expectedType ?? 'STRING',
    minText: assertion.min === undefined ? '' : String(assertion.min),
    maxText: assertion.max === undefined ? '' : String(assertion.max),
    maxMillis: assertion.maxMillis ?? 1500,
  }))
  dialogVisible.value = true
}

function addAssertion() {
  form.assertions.push({
    type: 'JSON_PATH_EXISTS', path: '$.', expectedText: '', expectedType: 'STRING',
    minText: '', maxText: '', maxMillis: 1500,
  })
}

function removeAssertion(index: number) {
  if (form.assertions.length === 1) {
    ElMessage.warning('至少保留一个断言')
    return
  }
  form.assertions.splice(index, 1)
}

function parseObject(text: string, label: string): Record<string, unknown> {
  const value = JSON.parse(text || '{}')
  if (value === null || Array.isArray(value) || typeof value !== 'object') {
    throw new Error(`${label}必须是 JSON 对象`)
  }
  return value as Record<string, unknown>
}

function parseExpected(draft: AssertionDraft): unknown {
  if (draft.type === 'STATUS_CODE') {
    const status = Number(draft.expectedText)
    if (!Number.isInteger(status)) throw new Error('状态码断言必须是整数')
    return status
  }
  if (draft.type !== 'JSON_VALUE_EQUALS') return undefined
  if (!draft.expectedText.trim()) throw new Error('JSON 值断言必须填写期望值')
  try {
    return JSON.parse(draft.expectedText)
  } catch {
    return draft.expectedText
  }
}

function parseOptionalNumber(value: string, label: string): number | undefined {
  if (!value.trim()) return undefined
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) throw new Error(`${label}必须是有效数值`)
  return parsed
}

function buildPayload(): SaveTestCasePayload {
  if (!form.name.trim()) throw new Error('请填写用例名称')
  if (!form.apiDefinitionId) throw new Error('请选择接口')
  if (form.assertions.length === 0) throw new Error('至少需要一个断言')

  const assertions = form.assertions.map((draft) => {
    if (['JSON_PATH_EXISTS', 'JSON_VALUE_EQUALS', 'JSON_TYPE', 'NUMBER_RANGE'].includes(draft.type)
      && !draft.path.trim()) {
      throw new Error('JSON 断言必须填写 JSONPath')
    }
    const assertion: AssertionConfig = {
      type: draft.type,
      expected: parseExpected(draft),
    }
    if (['JSON_PATH_EXISTS', 'JSON_VALUE_EQUALS', 'JSON_TYPE', 'NUMBER_RANGE'].includes(draft.type)) {
      assertion.path = draft.path.trim()
    }
    if (draft.type === 'JSON_TYPE') assertion.expectedType = draft.expectedType
    if (draft.type === 'NUMBER_RANGE') {
      assertion.min = parseOptionalNumber(draft.minText, '最小值')
      assertion.max = parseOptionalNumber(draft.maxText, '最大值')
      if (assertion.min === undefined && assertion.max === undefined) {
        throw new Error('数值范围断言至少填写一个边界')
      }
      if (assertion.min !== undefined && assertion.max !== undefined && assertion.min > assertion.max) {
        throw new Error('数值范围断言的最小值不能大于最大值')
      }
    }
    if (draft.type === 'RESPONSE_TIME') assertion.maxMillis = draft.maxMillis
    if (assertion.expected === undefined) delete assertion.expected
    return assertion
  })

  const body = form.bodyText.trim() ? JSON.parse(form.bodyText) : null
  return {
    projectId: selectedProjectId.value,
    apiDefinitionId: form.apiDefinitionId,
    name: form.name.trim(),
    scenarioType: form.scenarioType,
    request: {
      pathParams: parseObject(form.pathParamsText, '路径参数'),
      queryParams: parseObject(form.queryParamsText, '查询参数'),
      headers: parseObject(form.headersText, '请求头') as Record<string, string>,
      body,
      timeoutMillis: form.timeoutMillis,
    },
    assertions,
    priority: form.priority,
  }
}

async function submitCase() {
  let payload: SaveTestCasePayload
  try {
    payload = buildPayload()
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '用例配置不完整')
    return
  }

  saving.value = true
  try {
    if (editingId.value) {
      await updateTestCase(editingId.value, payload)
      ElMessage.success('用例已更新并重置为草稿，请重新审核')
    } else {
      await createTestCase(payload)
      ElMessage.success('用例已创建为草稿，请审核后执行')
    }
    dialogVisible.value = false
    await loadProjectData()
  } catch {
    ElMessage.error('用例保存失败，请检查配置')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(testCase: TestCase, enabled: boolean) {
  try {
    await setTestCaseEnabled(testCase.id, enabled)
    ElMessage.success(enabled ? '用例已启用' : '用例已停用')
  } catch {
    ElMessage.error('状态更新失败')
    await loadProjectData()
  }
}

async function approveCase(testCase: TestCase) {
  try {
    const { value } = await ElMessageBox.prompt(
      `确认已人工检查用例“${testCase.name}”的目标、请求和断言。请输入本地审批令牌：`,
      '人工审核与执行授权',
      { inputType: 'password', inputPlaceholder: 'LOCAL_APPROVAL_TOKEN', confirmButtonText: '审核通过' },
    )
    await reviewTestCase(testCase.id, value)
    ElMessage.success('审核状态已由服务端更新为 REVIEWED')
    await loadProjectData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('审核失败：请检查本地审批令牌和后端配置')
  }
}

async function removeCase(testCase: TestCase) {
  try {
    await ElMessageBox.confirm(`确认删除用例“${testCase.name}”？`, '删除用例', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteTestCase(testCase.id)
    ElMessage.success('用例已删除')
    await loadProjectData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('用例删除失败')
  }
}

async function runCases(items: TestCase[]) {
  if (!selectedProjectId.value || items.length === 0) return
  const blocked = items.find((item) => !item.enabled || item.reviewStatus !== 'REVIEWED')
  if (blocked) {
    ElMessage.warning('只有已启用且已审核的用例才会发送真实请求')
    return
  }
  executing.value = true
  try {
    const createdRun = await executeTestCases(
      selectedProjectId.value,
      items.map((item) => item.id),
      `手动执行 - ${new Date().toLocaleString('zh-CN')}`,
    )
    currentRun.value = createdRun
    resultDrawerVisible.value = true
    ElMessage.success('批量任务已创建')
    currentRun.value = await waitForTestRun(createdRun.id, (run) => {
      currentRun.value = run
    })
    if (currentRun.value.status === 'COMPLETED') {
      ElMessage.success('执行完成，结果已持久化')
    } else {
      ElMessage.error('批量任务执行失败')
    }
  } catch {
    ElMessage.error('执行失败，请确认演示 API 已在 8081 端口启动')
  } finally {
    executing.value = false
  }
}

function definitionLabel(id: string) {
  const definition = definitions.value.find((item) => item.id === id)
  return definition ? `${definition.method} ${definition.path}` : id
}

function resultTagType(status: string) {
  const mapping: Record<string, 'success' | 'danger' | 'warning' | 'info'> = {
    PASS: 'success',
    FAIL: 'danger',
    ERROR: 'warning',
    SKIPPED: 'info',
  }
  return mapping[status] ?? 'info'
}

function prettyEvidence(value?: string) {
  if (!value) return '无'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

watch(selectedProjectId, loadProjectData)
watch(reviewFilter, loadProjectData)
onMounted(initialize)
</script>

<template>
  <div class="cases-page">
    <section class="page-intro">
      <div>
        <span class="section-label">FR-02 测试用例与单条执行</span>
        <h2>把请求、断言与执行证据放在一起</h2>
        <p>配置手工用例并对登记目标发送真实 HTTP 请求。断言结果由确定性规则引擎产生并持久化。</p>
      </div>
      <div class="action-row">
        <el-button :icon="Refresh" @click="loadProjectData">刷新</el-button>
        <el-button
          type="success"
          :icon="VideoPlay"
          :loading="executing"
          :disabled="!canExecuteSelected"
          @click="runCases(selectedCases)"
        >
          执行已选 {{ selectedCases.length || '' }}
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建用例</el-button>
      </div>
    </section>

    <section class="panel case-toolbar">
      <div class="filter-item">
        <span>当前项目</span>
        <el-select v-model="selectedProjectId" placeholder="选择项目" style="width: 280px">
          <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
        </el-select>
      </div>
      <div class="filter-item">
        <span>审核状态</span>
        <el-select v-model="reviewFilter" style="width: 150px">
          <el-option label="全部" value="" />
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已审核" value="REVIEWED" />
          <el-option label="已停用" value="DISABLED" />
        </el-select>
      </div>
      <span class="toolbar-note">
        {{ selectedProject ? `已登记 ${definitions.length} 个接口` : '请选择项目' }}
      </span>
    </section>

    <section class="panel table-panel case-table-panel">
      <el-table
        v-if="cases.length || loading"
        v-loading="loading"
        :data="cases"
        stripe
        @selection-change="selectedCases = $event"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="name" label="用例名称" min-width="190" />
        <el-table-column label="接口" min-width="230" show-overflow-tooltip>
          <template #default="scope"><code>{{ definitionLabel(scope.row.apiDefinitionId) }}</code></template>
        </el-table-column>
        <el-table-column label="场景" width="90" align="center">
          <template #default="scope">
            {{ { POSITIVE: '正向', NEGATIVE: '反向', BOUNDARY: '边界' }[scope.row.scenarioType as string] }}
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
        <el-table-column label="审核" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.reviewStatus === 'REVIEWED' ? 'success' : 'info'" effect="light">
              {{ { DRAFT: '草稿', REVIEWED: '已审核', DISABLED: '已停用' }[scope.row.reviewStatus as string] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="78" align="center">
          <template #default="scope">
            <el-switch v-model="scope.row.enabled" @change="toggleEnabled(scope.row, Boolean($event))" />
          </template>
        </el-table-column>
        <el-table-column label="版本" width="70" align="center">
          <template #default="scope">v{{ scope.row.version }}</template>
        </el-table-column>
        <el-table-column label="操作" width="290" fixed="right">
          <template #default="scope">
            <el-button link type="success" :icon="VideoPlay" :loading="executing" @click="runCases([scope.row])">执行</el-button>
            <el-button v-if="scope.row.reviewStatus === 'DRAFT'" link type="warning" @click="approveCase(scope.row)">人工审核</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEditDialog(scope.row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="removeCase(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="当前项目还没有测试用例">
        <el-button type="primary" @click="openCreateDialog">创建第一条用例</el-button>
      </el-empty>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑测试用例' : '新建测试用例'"
      width="min(920px, 94vw)"
      top="4vh"
    >
      <el-form :model="form" label-position="top" class="case-form">
        <div class="form-grid three">
          <el-form-item label="用例名称">
            <el-input v-model="form.name" maxlength="180" show-word-limit placeholder="例如：查询已存在车辆" />
          </el-form-item>
          <el-form-item label="接口">
            <el-select v-model="form.apiDefinitionId" filterable>
              <el-option
                v-for="definition in definitions"
                :key="definition.id"
                :label="`${definition.method} ${definition.path}`"
                :value="definition.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="场景 / 优先级">
            <div class="inline-fields">
              <el-select v-model="form.scenarioType">
                <el-option label="正向" value="POSITIVE" />
                <el-option label="反向" value="NEGATIVE" />
                <el-option label="边界" value="BOUNDARY" />
              </el-select>
              <el-select v-model="form.priority">
                <el-option v-for="priority in ['P0', 'P1', 'P2', 'P3']" :key="priority" :label="priority" :value="priority" />
              </el-select>
            </div>
          </el-form-item>
        </div>

        <div class="form-grid four">
          <el-form-item label="路径参数 JSON">
            <el-input v-model="form.pathParamsText" type="textarea" :rows="4" placeholder='{"id":"1001"}' />
          </el-form-item>
          <el-form-item label="查询参数 JSON">
            <el-input v-model="form.queryParamsText" type="textarea" :rows="4" placeholder='{"minPrice":100000}' />
          </el-form-item>
          <el-form-item label="请求头 JSON">
            <el-input v-model="form.headersText" type="textarea" :rows="4" placeholder='敏感头仅允许 {"Authorization":"{{env:TEST_API_TOKEN}}"}' />
          </el-form-item>
          <el-form-item label="请求体 JSON（可选）">
            <el-input v-model="form.bodyText" type="textarea" :rows="4" placeholder='{"carId":"1001","quantity":1}' />
          </el-form-item>
        </div>

        <div class="form-grid two compact">
          <el-form-item label="超时时间（毫秒）">
            <el-input-number v-model="form.timeoutMillis" :min="100" :max="30000" :step="500" />
          </el-form-item>
          <el-form-item label="审核状态">
            <el-alert title="保存后由服务端设为草稿；编辑已审核用例会要求重新审核" type="info" :closable="false" />
          </el-form-item>
        </div>

        <div class="assertion-head">
          <div><strong>断言配置</strong><span>支持状态码、JSONPath 存在性与值相等</span></div>
          <el-button :icon="Plus" @click="addAssertion">添加断言</el-button>
        </div>
        <div v-for="(assertion, index) in form.assertions" :key="index" class="assertion-row">
          <el-select v-model="assertion.type" style="width: 190px">
            <el-option label="状态码相等" value="STATUS_CODE" />
            <el-option label="JSONPath 存在" value="JSON_PATH_EXISTS" />
            <el-option label="JSON 值相等" value="JSON_VALUE_EQUALS" />
            <el-option label="JSON 字段类型" value="JSON_TYPE" />
            <el-option label="数值范围" value="NUMBER_RANGE" />
            <el-option label="响应耗时" value="RESPONSE_TIME" />
          </el-select>
          <el-input
            v-if="['JSON_PATH_EXISTS', 'JSON_VALUE_EQUALS', 'JSON_TYPE', 'NUMBER_RANGE'].includes(assertion.type)"
            v-model="assertion.path"
            placeholder="JSONPath，例如 $.data.brand"
          />
          <el-input
            v-if="assertion.type === 'STATUS_CODE' || assertion.type === 'JSON_VALUE_EQUALS'"
            v-model="assertion.expectedText"
            :placeholder="assertion.type === 'STATUS_CODE' ? '例如 200' : '例如 &quot;比亚迪&quot; 或 2'"
          />
          <el-select v-if="assertion.type === 'JSON_TYPE'" v-model="assertion.expectedType">
            <el-option v-for="item in ['STRING', 'NUMBER', 'INTEGER', 'BOOLEAN', 'OBJECT', 'ARRAY', 'NULL']" :key="item" :label="item" :value="item" />
          </el-select>
          <template v-if="assertion.type === 'NUMBER_RANGE'">
            <el-input v-model="assertion.minText" placeholder="最小值（可选）" />
            <el-input v-model="assertion.maxText" placeholder="最大值（可选）" />
          </template>
          <el-input-number
            v-if="assertion.type === 'RESPONSE_TIME'"
            v-model="assertion.maxMillis"
            :min="1"
            :max="30000"
            :step="100"
            controls-position="right"
          />
          <el-button circle plain type="danger" :icon="Delete" @click="removeAssertion(index)" />
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCase">保存用例</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="resultDrawerVisible" title="执行结果与证据" size="min(760px, 92vw)">
      <template v-if="currentRun">
        <div class="run-summary">
          <div><span>总数</span><strong>{{ currentRun.totalCount }}</strong></div>
          <div class="pass"><span>PASS</span><strong>{{ currentRun.passCount }}</strong></div>
          <div class="fail"><span>FAIL</span><strong>{{ currentRun.failCount }}</strong></div>
          <div class="error"><span>ERROR</span><strong>{{ currentRun.errorCount }}</strong></div>
          <div><span>SKIPPED</span><strong>{{ currentRun.skippedCount }}</strong></div>
        </div>
        <el-collapse accordion>
          <el-collapse-item v-for="result in currentRun.results" :key="result.id" :name="result.id">
            <template #title>
              <div class="result-title">
                <el-tag :type="resultTagType(result.resultStatus)" effect="dark">{{ result.resultStatus }}</el-tag>
                <strong>{{ result.caseName }}</strong>
                <span v-if="result.statusCode">HTTP {{ result.statusCode }}</span>
                <span v-if="result.durationMs !== undefined">{{ result.durationMs }} ms</span>
              </div>
            </template>
            <el-alert v-if="result.errorMessage" :title="result.errorMessage" type="warning" :closable="false" />
            <h4>断言明细</h4>
            <el-table :data="result.assertions" size="small" border>
              <el-table-column label="结果" width="78" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.passed ? 'success' : 'danger'" size="small">
                    {{ scope.row.passed ? 'PASS' : 'FAIL' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="assertionType" label="类型" width="160" />
              <el-table-column prop="message" label="说明" min-width="230" />
            </el-table>
            <div class="evidence-grid">
              <div><h4>请求摘要</h4><pre>{{ prettyEvidence(result.requestSummary) }}</pre></div>
              <div><h4>响应摘要</h4><pre>{{ prettyEvidence(result.responseSummary) }}</pre></div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.case-toolbar {
  display: flex;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 22px;
  padding: 18px 22px;
}

.filter-item {
  display: grid;
  gap: 7px;
}

.filter-item > span,
.toolbar-note {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.toolbar-note {
  margin-left: auto;
  padding-bottom: 10px;
}

.case-table-panel {
  padding-top: 18px;
}

code {
  color: var(--app-accent-strong);
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 12px;
}

.form-grid {
  display: grid;
  gap: 16px;
}

.form-grid.three {
  grid-template-columns: 1.2fr 1fr 1fr;
}

.form-grid.four {
  grid-template-columns: repeat(2, 1fr);
}

.form-grid.two {
  grid-template-columns: repeat(2, 1fr);
}

.form-grid.compact {
  max-width: 560px;
}

.inline-fields,
.assertion-row {
  display: flex;
  width: 100%;
  gap: 10px;
}

.assertion-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0 12px;
  padding-top: 18px;
  border-top: 1px solid var(--app-border-light);
}

.assertion-head strong,
.assertion-head span {
  display: block;
}

.assertion-head span {
  margin-top: 4px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.assertion-row {
  margin-bottom: 10px;
}

.run-summary {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 22px;
}

.run-summary > div {
  padding: 14px;
  border-radius: 12px;
  background: var(--app-page-bg);
  text-align: center;
}

.run-summary span,
.run-summary strong {
  display: block;
}

.run-summary span {
  color: var(--app-text-muted);
  font-size: 11px;
}

.run-summary strong {
  margin-top: 5px;
  color: var(--app-text-primary);
  font-size: 22px;
}

.run-summary .pass { background: var(--app-accent-soft); }
.run-summary .fail { background: #fff0f0; }
.run-summary .error { background: #fff5df; }

.result-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.result-title span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.evidence-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.evidence-grid h4 {
  margin-bottom: 8px;
}

pre {
  max-height: 260px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  border-radius: 10px;
  color: var(--app-text-on-dark);
  background: var(--app-sidebar-bg);
  font: 12px/1.6 "Cascadia Code", Consolas, monospace;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 850px) {
  .form-grid.three,
  .form-grid.four,
  .form-grid.two,
  .evidence-grid {
    grid-template-columns: 1fr;
  }

  .case-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-note {
    margin-left: 0;
  }

  .run-summary {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
