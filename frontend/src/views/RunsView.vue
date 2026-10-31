<script setup lang="ts">
import { Document, Refresh, RefreshRight, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listProjects, type Project } from '../api/projects'
import {
  getTestRun,
  listTestRuns,
  rerunFailed,
  type TestRun,
} from '../api/testRuns'

const projects = ref<Project[]>([])
const router = useRouter()
const route = useRoute()
const selectedProjectId = ref('')
const runs = ref<TestRun[]>([])
const loading = ref(false)
const rerunning = ref(false)
const drawerVisible = ref(false)
const selectedRun = ref<TestRun | null>(null)
let refreshTimer: number | undefined

const hasRunningTask = computed(() => runs.value.some((run) => run.status === 'RUNNING'))

async function initialize() {
  try {
    projects.value = await listProjects()
    const requestedProject = String(route.query.projectId || '')
    if (projects.value.length > 0) selectedProjectId.value =
      projects.value.find((item) => item.id === requestedProject)?.id || projects.value[0].id
    if (route.query.runId) {
      selectedRun.value = await getTestRun(String(route.query.runId))
      drawerVisible.value = true
    }
  } catch {
    ElMessage.error('项目加载失败')
  }
}

async function loadRuns(silent = false) {
  if (!selectedProjectId.value) return
  if (!silent) loading.value = true
  try {
    runs.value = await listTestRuns(selectedProjectId.value)
    if (selectedRun.value?.status === 'RUNNING') {
      selectedRun.value = await getTestRun(selectedRun.value.id)
    }
    scheduleRefresh()
  } catch {
    if (!silent) ElMessage.error('测试任务加载失败')
  } finally {
    loading.value = false
  }
}

function scheduleRefresh() {
  if (refreshTimer !== undefined) window.clearTimeout(refreshTimer)
  if (hasRunningTask.value) {
    refreshTimer = window.setTimeout(() => loadRuns(true), 800)
  }
}

async function openRun(run: TestRun) {
  try {
    selectedRun.value = await getTestRun(run.id)
    drawerVisible.value = true
  } catch {
    ElMessage.error('任务详情加载失败')
  }
}

async function retryFailed(run: TestRun) {
  rerunning.value = true
  try {
    const created = await rerunFailed(run.id)
    ElMessage.success('失败用例重跑任务已创建')
    selectedRun.value = created
    drawerVisible.value = true
    await loadRuns(true)
  } catch {
    ElMessage.error('重跑失败，请确认原任务包含 FAIL 或 ERROR')
  } finally {
    rerunning.value = false
  }
}

function statusType(status: string) {
  return status === 'COMPLETED' ? 'success' : status === 'FAILED' ? 'danger' : 'warning'
}

function resultType(status: string) {
  return status === 'PASS' ? 'success' : status === 'FAIL' ? 'danger' : status === 'ERROR' ? 'warning' : 'info'
}

function progress(run: TestRun) {
  return run.totalCount === 0 ? 0 : Math.round((run.processedCount / run.totalCount) * 100)
}

watch(selectedProjectId, () => loadRuns())
onMounted(initialize)
onUnmounted(() => {
  if (refreshTimer !== undefined) window.clearTimeout(refreshTimer)
})
</script>

<template>
  <div class="runs-page">
    <section class="page-intro">
      <div>
        <span class="section-label">FR-04 批量执行与 FR-06 历史</span>
        <h2>追踪每一批用例的进度和结果</h2>
        <p>任务异步执行，单条失败或异常不会中断后续用例。失败重跑会创建新运行并保留父任务关联。</p>
      </div>
      <div class="action-row">
        <el-button :icon="Refresh" @click="loadRuns()">刷新</el-button>
      </div>
    </section>

    <section class="panel run-toolbar">
      <span>当前项目</span>
      <el-select v-model="selectedProjectId" style="width: 300px" placeholder="选择项目">
        <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
      </el-select>
      <el-tag v-if="hasRunningTask" type="warning" effect="plain">有任务正在执行，页面自动刷新</el-tag>
    </section>

    <section class="panel table-panel run-table-panel">
      <el-table v-if="runs.length || loading" v-loading="loading" :data="runs" stripe>
        <el-table-column prop="runName" label="任务名称" min-width="210" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.status)" effect="light">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="180">
          <template #default="scope">
            <el-progress :percentage="progress(scope.row)" :status="scope.row.status === 'FAILED' ? 'exception' : undefined" />
          </template>
        </el-table-column>
        <el-table-column label="PASS" width="72" align="center" prop="passCount" />
        <el-table-column label="FAIL" width="72" align="center" prop="failCount" />
        <el-table-column label="ERROR" width="78" align="center" prop="errorCount" />
        <el-table-column label="SKIP" width="72" align="center" prop="skippedCount" />
        <el-table-column label="触发方式" width="95" align="center" prop="triggerType" />
        <el-table-column label="开始时间" width="180">
          <template #default="scope">{{ new Date(scope.row.startedAt).toLocaleString('zh-CN') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :icon="View" @click="openRun(scope.row)">详情</el-button>
            <el-button
              link
              type="warning"
              :icon="RefreshRight"
              :loading="rerunning"
              :disabled="scope.row.status === 'RUNNING' || scope.row.failCount + scope.row.errorCount === 0"
              @click="retryFailed(scope.row)"
            >重跑失败</el-button>
            <el-button link type="primary" :icon="Document" @click="router.push({ path: '/reports', query: { projectId: scope.row.projectId, runId: scope.row.id } })">报告</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="还没有测试任务，请在测试用例页发起执行" />
    </section>

    <el-drawer v-model="drawerVisible" title="测试任务详情" size="min(820px, 94vw)">
      <template v-if="selectedRun">
        <div class="run-overview">
          <div><span>执行进度</span><strong>{{ selectedRun.processedCount }}/{{ selectedRun.totalCount }}</strong></div>
          <div><span>PASS</span><strong>{{ selectedRun.passCount }}</strong></div>
          <div><span>FAIL</span><strong>{{ selectedRun.failCount }}</strong></div>
          <div><span>ERROR</span><strong>{{ selectedRun.errorCount }}</strong></div>
        </div>
        <el-alert
          v-if="selectedRun.parentRunId"
          :title="`本任务由失败重跑创建，父任务 ${selectedRun.parentRunId}`"
          type="info"
          :closable="false"
          show-icon
        />
        <el-table :data="selectedRun.results" stripe class="result-table">
          <el-table-column label="结果" width="90">
            <template #default="scope"><el-tag :type="resultType(scope.row.resultStatus)">{{ scope.row.resultStatus }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="caseName" label="用例" min-width="190" />
          <el-table-column prop="statusCode" label="HTTP" width="80" align="center" />
          <el-table-column label="耗时" width="90" align="center">
            <template #default="scope">{{ scope.row.durationMs === undefined ? '--' : `${scope.row.durationMs} ms` }}</template>
          </el-table-column>
          <el-table-column label="断言" min-width="220">
            <template #default="scope">
              <div v-for="assertion in scope.row.assertions" :key="assertion.id" class="assertion-line">
                <span :class="assertion.passed ? 'passed' : 'failed'">{{ assertion.passed ? 'PASS' : 'FAIL' }}</span>
                {{ assertion.message }}
              </div>
              <span v-if="scope.row.errorMessage">{{ scope.row.errorMessage }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.run-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
  padding: 18px 22px;
}

.run-toolbar > span:first-child {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.run-table-panel {
  padding-top: 18px;
}

.run-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 18px;
}

.run-overview > div {
  padding: 15px;
  border-radius: 12px;
  background: var(--app-surface-subtle);
  text-align: center;
}

.run-overview span,
.run-overview strong {
  display: block;
}

.run-overview span { color: var(--app-text-muted); font-size: 11px; }
.run-overview strong { margin-top: 5px; color: var(--app-text-primary); font-size: 21px; }
.result-table { margin-top: 18px; }
.assertion-line { margin: 3px 0; color: var(--app-text-secondary); font-size: 12px; }
.assertion-line span { margin-right: 6px; font-weight: 800; }
.assertion-line .passed { color: var(--app-accent); }
.assertion-line .failed { color: #c0392b; }

@media (max-width: 700px) {
  .run-toolbar { align-items: stretch; flex-direction: column; }
  .run-overview { grid-template-columns: repeat(2, 1fr); }
}
</style>
