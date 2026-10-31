<script setup lang="ts">
import { Download, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listProjects, type Project } from '../api/projects'
import {
  downloadTestRunReport,
  getTestRunReport,
  listTestRuns,
  type ResultStatus,
  type TestRun,
  type TestRunReport,
} from '../api/testRuns'

const route = useRoute()
const projects = ref<Project[]>([])
const selectedProjectId = ref('')
const runs = ref<TestRun[]>([])
const selectedRunId = ref('')
const report = ref<TestRunReport | null>(null)
const loadingRuns = ref(false)
const loadingReport = ref(false)
const downloading = ref(false)

const distribution = computed(() => {
  const values = report.value?.resultDistribution
  return [
    { status: 'PASS' as ResultStatus, count: values?.PASS ?? 0, color: 'var(--app-accent)' },
    { status: 'FAIL' as ResultStatus, count: values?.FAIL ?? 0, color: '#c0392b' },
    { status: 'ERROR' as ResultStatus, count: values?.ERROR ?? 0, color: '#b66a00' },
    { status: 'SKIPPED' as ResultStatus, count: values?.SKIPPED ?? 0, color: 'var(--app-text-muted)' },
  ]
})

async function initialize() {
  try {
    projects.value = await listProjects()
    const requestedProject = String(route.query.projectId ?? '')
    selectedProjectId.value = projects.value.some((project) => project.id === requestedProject)
      ? requestedProject
      : projects.value[0]?.id ?? ''
  } catch {
    ElMessage.error('项目加载失败')
  }
}

async function loadRuns() {
  if (!selectedProjectId.value) return
  loadingRuns.value = true
  report.value = null
  try {
    runs.value = await listTestRuns(selectedProjectId.value)
    const requestedRun = String(route.query.runId ?? '')
    selectedRunId.value = runs.value.some((run) => run.id === requestedRun)
      ? requestedRun
      : runs.value[0]?.id ?? ''
  } catch {
    ElMessage.error('运行历史加载失败')
  } finally {
    loadingRuns.value = false
  }
}

async function loadReport() {
  if (!selectedRunId.value) {
    report.value = null
    return
  }
  loadingReport.value = true
  try {
    report.value = await getTestRunReport(selectedRunId.value)
  } catch {
    ElMessage.error('运行报告加载失败')
  } finally {
    loadingReport.value = false
  }
}

async function downloadReport() {
  if (!report.value) return
  downloading.value = true
  try {
    await downloadTestRunReport(report.value.run.id, report.value.run.runName)
    ElMessage.success('HTML 报告已开始下载')
  } catch {
    ElMessage.error('运行报告下载失败')
  } finally {
    downloading.value = false
  }
}

function statusType(status: string) {
  return status === 'PASS' || status === 'COMPLETED' ? 'success'
    : status === 'FAIL' || status === 'FAILED' ? 'danger'
      : status === 'ERROR' ? 'warning' : 'info'
}

function formatDate(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN') : '--'
}

function failureEvidence(result: TestRunReport['run']['results'][number]) {
  if (result.errorMessage) return result.errorMessage
  return result.assertions.filter((item) => !item.passed).map((item) => item.message).join('；') || '--'
}

watch(selectedProjectId, loadRuns)
watch(selectedRunId, loadReport)
onMounted(initialize)
</script>

<template>
  <div class="reports-page">
    <section class="page-intro">
      <div>
        <span class="section-label">FR-06 结果分析与报告</span>
        <h2>用运行证据生成可分享的测试报告</h2>
        <p>报告汇总通过率、耗时分布和失败证据，并可下载为独立 HTML 文件用于归档或打印。</p>
      </div>
      <div class="report-actions">
        <el-button :icon="Refresh" @click="loadReport">刷新</el-button>
        <el-button type="primary" :icon="Download" :disabled="!report" :loading="downloading" @click="downloadReport">下载 HTML 报告</el-button>
      </div>
    </section>

    <section class="panel report-toolbar">
      <div><span>项目</span><el-select v-model="selectedProjectId" placeholder="选择项目"><el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" /></el-select></div>
      <div><span>测试任务</span><el-select v-model="selectedRunId" v-loading="loadingRuns" filterable placeholder="选择测试任务"><el-option v-for="run in runs" :key="run.id" :label="`${run.runName} · ${formatDate(run.startedAt)}`" :value="run.id"><span>{{ run.runName }}</span><el-tag :type="statusType(run.status)" size="small">{{ run.status }}</el-tag></el-option></el-select></div>
    </section>

    <div v-if="report" v-loading="loadingReport" class="report-content">
      <section class="report-hero">
        <div><span class="section-label">{{ report.run.triggerType }} · {{ formatDate(report.run.startedAt) }}</span><h2>{{ report.run.runName }}</h2><p>任务 ID：{{ report.run.id }}</p></div>
        <el-tag :type="statusType(report.run.status)" size="large">{{ report.run.status }}</el-tag>
      </section>

      <section class="report-metrics">
        <div><span>总用例</span><strong>{{ report.run.totalCount }}</strong><small>{{ report.run.processedCount }} 条已处理</small></div>
        <div><span>通过率</span><strong>{{ report.passRate.toFixed(1) }}%</strong><small>{{ report.run.passCount }} PASS</small></div>
        <div><span>平均耗时</span><strong>{{ report.averageDurationMs.toFixed(1) }} ms</strong><small>总计 {{ report.totalDurationMs }} ms</small></div>
        <div><span>P95 耗时</span><strong>{{ report.p95DurationMs }} ms</strong><small>最大 {{ report.maxDurationMs }} ms</small></div>
      </section>

      <section class="report-grid">
        <article class="panel distribution-panel">
          <div class="panel-head"><div><span class="section-label">结果分布</span><h3>确定性状态统计</h3></div></div>
          <div v-for="item in distribution" :key="item.status" class="distribution-row">
            <span>{{ item.status }}</span>
            <div><i :style="{ width: `${report.run.totalCount ? item.count / report.run.totalCount * 100 : 0}%`, background: item.color }"></i></div>
            <strong>{{ item.count }}</strong>
          </div>
        </article>
        <article class="panel failure-panel">
          <div class="panel-head"><div><span class="section-label">失败摘要</span><h3>需要关注的用例</h3></div><el-tag type="danger" effect="plain">{{ report.failures.length }}</el-tag></div>
          <div v-if="report.failures.length" class="failure-list">
            <div v-for="failure in report.failures" :key="failure.id">
              <el-tag :type="statusType(failure.resultStatus)" size="small">{{ failure.resultStatus }}</el-tag>
              <strong>{{ failure.caseName }}</strong>
              <p>{{ failureEvidence(failure) }}</p>
            </div>
          </div>
          <el-empty v-else description="本次运行没有失败或异常" :image-size="70" />
        </article>
      </section>

      <section class="panel table-panel report-table">
        <div class="panel-head"><div><span class="section-label">执行明细</span><h3>请求结果与断言证据</h3></div><span class="generated-at">报告生成于 {{ formatDate(report.generatedAt) }}</span></div>
        <el-table :data="report.run.results" stripe>
          <el-table-column label="结果" width="95"><template #default="scope"><el-tag :type="statusType(scope.row.resultStatus)">{{ scope.row.resultStatus }}</el-tag></template></el-table-column>
          <el-table-column prop="caseName" label="用例" min-width="190" />
          <el-table-column prop="statusCode" label="HTTP" width="80" align="center" />
          <el-table-column label="耗时" width="100" align="center"><template #default="scope">{{ scope.row.durationMs === undefined ? '--' : `${scope.row.durationMs} ms` }}</template></el-table-column>
          <el-table-column label="证据" min-width="280"><template #default="scope"><div v-for="assertion in scope.row.assertions" :key="assertion.id" class="evidence-line"><span :class="assertion.passed ? 'passed' : 'failed'">{{ assertion.passed ? 'PASS' : 'FAIL' }}</span>{{ assertion.message }}</div><div v-if="scope.row.errorMessage">{{ scope.row.errorMessage }}</div></template></el-table-column>
        </el-table>
      </section>
    </div>

    <section v-else class="panel empty-report" v-loading="loadingReport || loadingRuns">
      <h3>选择一次测试任务</h3><p>这里会展示运行汇总、耗时指标、失败证据和完整结果。</p>
    </section>
  </div>
</template>

<style scoped>
.report-actions { display: flex; gap: 10px; }
.report-toolbar { display: grid; grid-template-columns: minmax(220px, 360px) minmax(340px, 1fr); gap: 18px; margin-bottom: 22px; padding: 18px 22px; }
.report-toolbar > div { display: grid; grid-template-columns: 70px minmax(0, 1fr); align-items: center; gap: 10px; }
.report-toolbar span { color: var(--app-text-muted); font-size: 12px; font-weight: 700; }
.report-toolbar .el-tag { float: right; }
.report-content { display: grid; gap: 22px; }
.report-hero { display: flex; align-items: center; justify-content: space-between; padding: 26px 30px; border-radius: 16px; color: #fff; background: linear-gradient(130deg, #103566, var(--app-accent)); }
.report-hero h2 { margin: 5px 0; font-size: 27px; }
.report-hero p { margin: 0; color: var(--app-hero-copy); font-size: 12px; }
.report-hero .section-label { color: var(--app-highlight); }
.report-metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.report-metrics > div { padding: 18px; border: 1px solid var(--app-border); border-radius: 13px; background: #fff; }
.report-metrics span, .report-metrics strong, .report-metrics small { display: block; }
.report-metrics span, .report-metrics small { color: var(--app-text-muted); }
.report-metrics span { font-size: 12px; }.report-metrics strong { margin: 5px 0; color: var(--app-text-primary); font-size: 23px; }.report-metrics small { font-size: 11px; }
.report-grid { display: grid; grid-template-columns: .8fr 1.2fr; gap: 22px; }
.distribution-panel, .failure-panel, .report-table { padding: 20px 22px; }
.distribution-row { display: grid; grid-template-columns: 70px 1fr 36px; align-items: center; gap: 10px; margin: 14px 0; }
.distribution-row > span { color: var(--app-text-secondary); font-size: 12px; font-weight: 700; }.distribution-row > div { overflow: hidden; height: 8px; border-radius: 999px; background: var(--app-inset-bg); }.distribution-row i { display: block; height: 100%; border-radius: inherit; }.distribution-row strong { text-align: right; }
.failure-list { display: grid; gap: 10px; }.failure-list > div { display: grid; grid-template-columns: 70px 1fr; align-items: center; gap: 8px; padding: 12px; border-radius: 10px; background: #faf5f3; }.failure-list p { grid-column: 2; margin: 0; color: #7c6a64; font-size: 12px; }
.generated-at { color: var(--app-text-muted); font-size: 11px; }
.evidence-line { margin: 3px 0; color: var(--app-text-secondary); font-size: 12px; }.evidence-line span { margin-right: 7px; font-weight: 800; }.evidence-line .passed { color: var(--app-accent); }.evidence-line .failed { color: #c0392b; }
.empty-report { display: grid; align-content: center; justify-items: center; min-height: 360px; padding: 40px; color: var(--app-text-muted); }.empty-report h3 { margin-bottom: 0; color: var(--app-text-primary); }
@media (max-width: 900px) { .report-metrics { grid-template-columns: repeat(2, 1fr); }.report-grid { grid-template-columns: 1fr; } }
@media (max-width: 700px) { .report-actions { align-items: stretch; flex-direction: column; }.report-toolbar { grid-template-columns: 1fr; }.report-toolbar > div { grid-template-columns: 1fr; }.report-hero { align-items: flex-start; flex-direction: column; gap: 15px; }.report-metrics { grid-template-columns: 1fr; } }
</style>
