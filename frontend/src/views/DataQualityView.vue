<script setup lang="ts">
import { DataAnalysis, Download, Refresh, UploadFilled, View } from '@element-plus/icons-vue'
import { ElMessage, type UploadFile, type UploadFiles, type UploadInstance } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import {
  analyzeDataQuality,
  downloadDataExport,
  getDataQualityTask,
  listDataQualityTasks,
  previewDataCleaning,
  uploadDataFile,
  type CleanDataRequest,
  type CleaningPreview,
  type ColumnCleaningRule,
  type ColumnQualityRule,
  type DataExportDataset,
  type DataExportFormat,
  type DataQualityIssue,
  type DataQualityTask,
  type DataRuleCode,
  type ExpectedDataType,
} from '../api/dataQuality'
import { listProjects, type Project } from '../api/projects'

const projects = ref<Project[]>([])
const selectedProjectId = ref('')
const tasks = ref<DataQualityTask[]>([])
const currentTask = ref<DataQualityTask | null>(null)
const selectedFile = ref<File | null>(null)
const uploadControl = ref<UploadInstance>()
const sheetName = ref('')
const loading = ref(false)
const uploading = ref(false)
const analyzing = ref(false)
const rules = ref<ColumnQualityRule[]>([])
const duplicateKeyColumns = ref<string[]>([])
const ruleFilter = ref<DataRuleCode | ''>('')
const columnFilter = ref('')
const cleaningRules = ref<ColumnCleaningRule[]>([])
const removeDuplicates = ref(false)
const cleaningDuplicateKeyColumns = ref<string[]>([])
const cleaningPreview = ref<CleaningPreview | null>(null)
const cleaning = ref(false)
const exporting = ref('')

const typeOptions: { label: string; value: ExpectedDataType }[] = [
  { label: '文本', value: 'STRING' },
  { label: '整数', value: 'INTEGER' },
  { label: '小数', value: 'DECIMAL' },
  { label: '布尔', value: 'BOOLEAN' },
  { label: '日期 yyyy-MM-dd', value: 'DATE' },
]
const ruleOptions: { label: string; value: DataRuleCode }[] = [
  { label: '必填缺失', value: 'REQUIRED' },
  { label: '业务键重复', value: 'DUPLICATE_KEY' },
  { label: '类型不匹配', value: 'TYPE_MISMATCH' },
  { label: '数值越界', value: 'NUM_RANGE' },
]

const filteredIssues = computed<DataQualityIssue[]>(() => (currentTask.value?.issues ?? []).filter((issue) => {
  const ruleMatches = !ruleFilter.value || issue.ruleCode === ruleFilter.value
  const columnMatches = !columnFilter.value || issue.columnName === columnFilter.value
  return ruleMatches && columnMatches
}))
const issueCount = computed(() => Number(currentTask.value?.summary.issueCount ?? currentTask.value?.issues.length ?? 0))

async function initialize() {
  try {
    projects.value = await listProjects()
    if (projects.value.length > 0) selectedProjectId.value = projects.value[0].id
  } catch {
    ElMessage.error('项目加载失败')
  }
}

async function loadTasks() {
  if (!selectedProjectId.value) return
  loading.value = true
  currentTask.value = null
  try {
    tasks.value = await listDataQualityTasks(selectedProjectId.value)
    if (tasks.value.length > 0) await selectTask(tasks.value[0])
  } catch {
    ElMessage.error('数据质量任务加载失败')
  } finally {
    loading.value = false
  }
}

function resetRules(task: DataQualityTask) {
  const savedRules = Array.isArray(task.summary.columns) ? task.summary.columns : []
  const savedByColumn = new Map(savedRules.map((rule) => [rule.columnName, rule]))
  rules.value = task.headers.map((columnName) => savedByColumn.get(columnName) ?? ({
    columnName,
    expectedType: 'STRING',
    required: false,
  }))
  duplicateKeyColumns.value = Array.isArray(task.summary.duplicateKeyColumns)
    ? [...task.summary.duplicateKeyColumns]
    : []
  ruleFilter.value = ''
  columnFilter.value = ''
  cleaningRules.value = task.headers.map((columnName) => ({
    columnName,
    trimWhitespace: true,
    emptyReplacement: undefined,
  }))
  cleaningDuplicateKeyColumns.value = Array.isArray(task.summary.duplicateKeyColumns)
    ? [...task.summary.duplicateKeyColumns]
    : []
  removeDuplicates.value = false
  cleaningPreview.value = null
}

async function selectTask(task: DataQualityTask) {
  try {
    currentTask.value = await getDataQualityTask(task.id)
    resetRules(currentTask.value)
  } catch {
    ElMessage.error('任务详情加载失败')
  }
}

function onFileChange(uploadFile: UploadFile, _uploadFiles: UploadFiles) {
  selectedFile.value = uploadFile.raw ?? null
}
function onFileRemove() { selectedFile.value = null }

async function upload() {
  if (!selectedProjectId.value) return ElMessage.warning('请先选择项目')
  if (!selectedFile.value) return ElMessage.warning('请选择 CSV 或 XLSX 文件')
  uploading.value = true
  try {
    const created = await uploadDataFile(selectedProjectId.value, selectedFile.value, sheetName.value)
    currentTask.value = created
    resetRules(created)
    selectedFile.value = null
    uploadControl.value?.clearFiles()
    sheetName.value = ''
    await refreshTaskList()
    ElMessage.success(`已解析 ${created.rowCount} 行、${created.columnCount} 列`)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '文件上传或解析失败')
  } finally {
    uploading.value = false
  }
}

async function refreshTaskList() {
  if (!selectedProjectId.value) return
  try {
    tasks.value = await listDataQualityTasks(selectedProjectId.value)
  } catch {
    ElMessage.error('任务列表刷新失败')
  }
}

function isNumeric(rule: ColumnQualityRule) {
  return rule.expectedType === 'INTEGER' || rule.expectedType === 'DECIMAL'
}

async function analyze() {
  if (!currentTask.value) return
  for (const rule of rules.value) {
    if (rule.min !== undefined && rule.max !== undefined && rule.min > rule.max) {
      ElMessage.warning(`${rule.columnName} 的最小值不能大于最大值`)
      return
    }
  }
  analyzing.value = true
  try {
    const payload = rules.value.map((rule) => ({
      ...rule,
      min: isNumeric(rule) ? rule.min : undefined,
      max: isNumeric(rule) ? rule.max : undefined,
    }))
    currentTask.value = await analyzeDataQuality(currentTask.value.id, payload, duplicateKeyColumns.value)
    await refreshTaskList()
    ElMessage.success(`检测完成，发现 ${currentTask.value.issues.length} 个质量问题`)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '质量检测失败')
  } finally {
    analyzing.value = false
  }
}

function cleaningRequest(): CleanDataRequest {
  return {
    columns: cleaningRules.value.map((rule) => ({
      ...rule,
      emptyReplacement: rule.emptyReplacement?.length ? rule.emptyReplacement : undefined,
    })),
    removeDuplicates: removeDuplicates.value,
    duplicateKeyColumns: cleaningDuplicateKeyColumns.value,
  }
}

async function previewCleaning() {
  if (!currentTask.value) return
  cleaning.value = true
  try {
    cleaningPreview.value = await previewDataCleaning(currentTask.value.id, cleaningRequest())
    ElMessage.success(
      `预览完成：修改 ${cleaningPreview.value.changedCellCount} 个单元格，移除 ${cleaningPreview.value.removedDuplicateCount} 条重复记录`,
    )
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '清洗预览失败')
  } finally {
    cleaning.value = false
  }
}

async function exportData(format: DataExportFormat, dataset: DataExportDataset) {
  if (!currentTask.value) return
  exporting.value = `${dataset}-${format}`
  try {
    await downloadDataExport(
      currentTask.value.id,
      format,
      dataset,
      cleaningRequest(),
      currentTask.value.fileName,
    )
    ElMessage.success(`${format} 导出已开始下载`)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? `${format} 导出失败`)
  } finally {
    exporting.value = ''
  }
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
function ruleLabel(code: DataRuleCode) { return ruleOptions.find((item) => item.value === code)?.label ?? code }
function ruleTagType(code: DataRuleCode) { return code === 'REQUIRED' ? 'danger' : code === 'DUPLICATE_KEY' ? 'warning' : 'info' }

watch(selectedProjectId, loadTasks)
onMounted(initialize)
</script>

<template>
  <div class="quality-page">
    <section class="page-intro">
      <div>
        <span class="section-label">FR-07 数据读取与 FR-08 质量检测</span>
        <h2>把 CSV / XLSX 变成可追溯的质量证据</h2>
        <p>原文件安全保存且不覆盖；解析结果、规则和问题清单均来自确定性检测。单文件限制 10 MB、10,000 行。</p>
      </div>
      <el-button :icon="Refresh" @click="refreshTaskList">刷新任务</el-button>
    </section>

    <section class="panel quality-toolbar">
      <div class="field-block project-field">
        <span>当前项目</span>
        <el-select v-model="selectedProjectId" placeholder="选择项目">
          <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
        </el-select>
      </div>
      <el-upload ref="uploadControl" drag :auto-upload="false" :limit="1" accept=".csv,.xlsx" :on-change="onFileChange" :on-remove="onFileRemove" class="file-drop">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖入 CSV / XLSX，或<em>点击选择</em></div>
        <template #tip><span>CSV 使用 UTF-8；XLSX 默认读取第一个工作表。</span></template>
      </el-upload>
      <div class="upload-actions">
        <el-input v-model="sheetName" clearable placeholder="XLSX 工作表名（可选）" />
        <el-button type="primary" :icon="DataAnalysis" :loading="uploading" @click="upload">上传并解析</el-button>
      </div>
    </section>

    <section class="quality-layout" v-loading="loading">
      <aside class="panel task-panel">
        <div class="panel-head compact-head">
          <div><span class="section-label">解析历史</span><h3>数据任务</h3></div>
          <el-tag effect="plain">{{ tasks.length }}</el-tag>
        </div>
        <div v-if="tasks.length" class="task-list">
          <button v-for="task in tasks" :key="task.id" type="button" :class="['task-item', { active: currentTask?.id === task.id }]" @click="selectTask(task)">
            <span class="task-name">{{ task.fileName }}</span>
            <span>{{ task.fileType }} · {{ task.rowCount }} 行 · {{ formatBytes(task.fileSize) }}</span>
            <el-tag size="small" :type="task.status === 'ANALYZED' ? 'success' : 'info'">{{ task.status }}</el-tag>
          </button>
        </div>
        <el-empty v-else description="尚未上传数据文件" :image-size="80" />
      </aside>

      <main v-if="currentTask" class="quality-main">
        <section class="metrics-strip">
          <div><span>数据行</span><strong>{{ currentTask.rowCount }}</strong></div>
          <div><span>字段数</span><strong>{{ currentTask.columnCount }}</strong></div>
          <div><span>空值率</span><strong>{{ (currentTask.emptyCellRate * 100).toFixed(2) }}%</strong></div>
          <div><span>质量问题</span><strong>{{ issueCount }}</strong></div>
        </section>

        <section class="panel table-panel preview-panel">
          <div class="panel-head compact-head">
            <div><span class="section-label">安全预览</span><h3>{{ currentTask.fileName }}</h3></div>
            <el-tag v-if="currentTask.sheetName" effect="plain">{{ currentTask.sheetName }}</el-tag>
          </div>
          <el-table :data="currentTask.previewRows" stripe max-height="340">
            <el-table-column type="index" label="#" width="55" fixed />
            <el-table-column v-for="header in currentTask.headers" :key="header" :prop="header" :label="header" min-width="140" show-overflow-tooltip />
          </el-table>
        </section>

        <section class="panel rule-panel">
          <div class="panel-head compact-head">
            <div><span class="section-label">P0 规则</span><h3>字段与业务键配置</h3></div>
            <el-button type="primary" :icon="DataAnalysis" :loading="analyzing" @click="analyze">开始检测</el-button>
          </div>
          <div class="duplicate-field">
            <span>重复业务键</span>
            <el-select v-model="duplicateKeyColumns" multiple collapse-tags clearable placeholder="不选择时按整行判断重复">
              <el-option v-for="header in currentTask.headers" :key="header" :label="header" :value="header" />
            </el-select>
          </div>
          <el-table :data="rules" stripe>
            <el-table-column prop="columnName" label="字段" min-width="150" />
            <el-table-column label="预期类型" min-width="180">
              <template #default="scope"><el-select v-model="scope.row.expectedType"><el-option v-for="option in typeOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></template>
            </el-table-column>
            <el-table-column label="必填" width="90" align="center"><template #default="scope"><el-switch v-model="scope.row.required" /></template></el-table-column>
            <el-table-column label="最小值" min-width="130"><template #default="scope"><el-input-number v-model="scope.row.min" :disabled="!isNumeric(scope.row)" controls-position="right" /></template></el-table-column>
            <el-table-column label="最大值" min-width="130"><template #default="scope"><el-input-number v-model="scope.row.max" :disabled="!isNumeric(scope.row)" controls-position="right" /></template></el-table-column>
          </el-table>
        </section>

        <section class="panel issue-panel">
          <div class="panel-head compact-head">
            <div><span class="section-label">问题明细</span><h3>定位到行、列和规则</h3></div>
            <div class="issue-filters">
              <el-select v-model="ruleFilter" clearable placeholder="全部规则"><el-option v-for="option in ruleOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select>
              <el-select v-model="columnFilter" clearable placeholder="全部字段"><el-option v-for="header in currentTask.headers" :key="header" :label="header" :value="header" /></el-select>
            </div>
          </div>
          <el-table v-if="filteredIssues.length" :data="filteredIssues" stripe max-height="460">
            <el-table-column prop="rowNumber" label="行" width="72" align="center" />
            <el-table-column prop="columnName" label="字段" min-width="130" />
            <el-table-column label="规则" min-width="145"><template #default="scope"><el-tag :type="ruleTagType(scope.row.ruleCode)">{{ ruleLabel(scope.row.ruleCode) }}</el-tag></template></el-table-column>
            <el-table-column prop="originalValue" label="原始值" min-width="150" show-overflow-tooltip />
            <el-table-column prop="description" label="说明" min-width="250" show-overflow-tooltip />
          </el-table>
          <el-empty v-else :description="currentTask.status === 'ANALYZED' ? '当前筛选下没有质量问题' : '配置规则后开始质量检测'" />
        </section>

        <section class="panel cleaning-panel">
          <div class="panel-head compact-head">
            <div><span class="section-label">非破坏式清洗</span><h3>预览改动后再导出</h3></div>
            <div class="cleaning-actions">
              <el-button :loading="cleaning" @click="previewCleaning">生成清洗预览</el-button>
              <el-button type="primary" :icon="Download" :loading="exporting === 'CLEANED-CSV'" @click="exportData('CSV', 'CLEANED')">清洗后 CSV</el-button>
              <el-button type="primary" :icon="Download" :loading="exporting === 'CLEANED-XLSX'" @click="exportData('XLSX', 'CLEANED')">清洗后 XLSX</el-button>
            </div>
          </div>
          <el-alert title="清洗只生成预览和下载文件，不修改原始上传文件。XLSX 会按已保存的质量规则写入数字、布尔和日期类型。" type="info" :closable="false" show-icon />
          <div class="duplicate-cleaning">
            <el-switch v-model="removeDuplicates" active-text="移除后出现的重复记录" />
            <el-select v-model="cleaningDuplicateKeyColumns" multiple collapse-tags clearable :disabled="!removeDuplicates" placeholder="不选择时按整行去重">
              <el-option v-for="header in currentTask.headers" :key="header" :label="header" :value="header" />
            </el-select>
          </div>
          <el-table :data="cleaningRules" stripe class="cleaning-rules-table">
            <el-table-column prop="columnName" label="字段" min-width="160" />
            <el-table-column label="去除首尾空白" width="150" align="center"><template #default="scope"><el-switch v-model="scope.row.trimWhitespace" /></template></el-table-column>
            <el-table-column label="空值替换" min-width="220"><template #default="scope"><el-input v-model="scope.row.emptyReplacement" clearable placeholder="留空表示不填充" maxlength="500" /></template></el-table-column>
          </el-table>

          <template v-if="cleaningPreview">
            <div class="cleaning-summary">
              <div><span>原始行数</span><strong>{{ cleaningPreview.originalRowCount }}</strong></div>
              <div><span>输出行数</span><strong>{{ cleaningPreview.outputRowCount }}</strong></div>
              <div><span>修改单元格</span><strong>{{ cleaningPreview.changedCellCount }}</strong></div>
              <div><span>移除重复行</span><strong>{{ cleaningPreview.removedDuplicateCount }}</strong></div>
            </div>
            <h4>清洗后数据预览</h4>
            <el-table :data="cleaningPreview.previewRows" stripe max-height="320">
              <el-table-column type="index" label="#" width="55" fixed />
              <el-table-column v-for="header in cleaningPreview.headers" :key="header" :prop="header" :label="header" min-width="140" show-overflow-tooltip />
            </el-table>
            <h4>改动明细</h4>
            <el-table v-if="cleaningPreview.changes.length" :data="cleaningPreview.changes" stripe max-height="300">
              <el-table-column prop="rowNumber" label="原行" width="80" align="center" />
              <el-table-column prop="columnName" label="字段" min-width="130" />
              <el-table-column prop="originalValue" label="原始值" min-width="150" show-overflow-tooltip />
              <el-table-column prop="cleanedValue" label="清洗值" min-width="150" show-overflow-tooltip />
              <el-table-column prop="reason" label="处理" min-width="150" />
            </el-table>
            <el-empty v-else description="当前规则没有修改任何单元格" :image-size="70" />
          </template>

          <div class="original-export">
            <span>保留原始数据副本：</span>
            <el-button text :icon="Download" :loading="exporting === 'ORIGINAL-CSV'" @click="exportData('CSV', 'ORIGINAL')">原始 CSV</el-button>
            <el-button text :icon="Download" :loading="exporting === 'ORIGINAL-XLSX'" @click="exportData('XLSX', 'ORIGINAL')">原始 XLSX</el-button>
          </div>
        </section>
      </main>

      <section v-else class="panel empty-workbench">
        <el-icon><View /></el-icon><h3>选择或上传一个数据文件</h3><p>文件解析成功后，这里会展示预览、字段规则和质量问题。</p>
      </section>
    </section>
  </div>
</template>

<style scoped>
.quality-toolbar { display: grid; grid-template-columns: 280px minmax(300px, 1fr) 280px; align-items: center; gap: 22px; margin-bottom: 22px; padding: 20px 22px; }
.field-block { display: grid; gap: 8px; }
.field-block > span, .duplicate-field > span { color: var(--app-text-muted); font-size: 12px; font-weight: 700; }
.file-drop :deep(.el-upload-dragger) { padding: 15px 12px; }
.file-drop :deep(.el-icon--upload) { margin-bottom: 2px; font-size: 30px; }
.file-drop :deep(.el-upload__tip) { margin: 5px 0 0; color: var(--app-text-muted); }
.upload-actions { display: grid; gap: 10px; }
.quality-layout { display: grid; grid-template-columns: 260px minmax(0, 1fr); align-items: start; gap: 22px; }
.task-panel { position: sticky; top: 22px; padding: 20px; }
.compact-head { align-items: center; margin-bottom: 16px; }
.compact-head h3 { margin: 3px 0 0; }
.task-list { display: grid; gap: 9px; }
.task-item { display: grid; justify-items: start; gap: 6px; width: 100%; padding: 13px; border: 1px solid var(--app-border); border-radius: 11px; color: var(--app-text-secondary); background: #fff; text-align: left; cursor: pointer; }
.task-item:hover, .task-item.active { border-color: var(--app-accent); background: var(--app-accent-soft); }
.task-name { overflow: hidden; max-width: 100%; color: var(--app-text-primary); font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.quality-main { display: grid; gap: 22px; min-width: 0; }
.metrics-strip { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.metrics-strip > div { padding: 16px 18px; border: 1px solid var(--app-border); border-radius: 13px; background: #fff; box-shadow: 0 8px 24px rgba(25, 62, 113, .05); }
.metrics-strip span, .metrics-strip strong { display: block; }
.metrics-strip span { color: var(--app-text-muted); font-size: 12px; }
.metrics-strip strong { margin-top: 5px; color: var(--app-text-primary); font-size: 24px; }
.preview-panel, .rule-panel, .issue-panel, .cleaning-panel { padding: 20px 22px; }
.duplicate-field { display: grid; grid-template-columns: 100px minmax(260px, 520px); align-items: center; gap: 12px; margin-bottom: 15px; }
.issue-filters { display: flex; gap: 10px; }
.issue-filters .el-select { width: 160px; }
.cleaning-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.duplicate-cleaning { display: grid; grid-template-columns: 230px minmax(260px, 520px); align-items: center; gap: 14px; margin: 18px 0 14px; }
.cleaning-rules-table { margin-bottom: 20px; }
.cleaning-summary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin: 20px 0; }
.cleaning-summary > div { padding: 13px 15px; border-radius: 11px; background: var(--app-surface-subtle); }
.cleaning-summary span, .cleaning-summary strong { display: block; }
.cleaning-summary span { color: var(--app-text-muted); font-size: 11px; }
.cleaning-summary strong { margin-top: 4px; color: var(--app-text-primary); font-size: 20px; }
.cleaning-panel h4 { margin: 20px 0 10px; color: var(--app-text-primary); }
.original-export { display: flex; align-items: center; justify-content: flex-end; gap: 4px; margin-top: 14px; color: var(--app-text-muted); font-size: 12px; }
.empty-workbench { min-height: 380px; display: grid; align-content: center; justify-items: center; padding: 48px; color: var(--app-text-muted); text-align: center; }
.empty-workbench .el-icon { color: var(--app-accent); font-size: 42px; }
.empty-workbench h3 { margin: 14px 0 4px; color: var(--app-text-primary); }
.empty-workbench p { margin: 0; }
@media (max-width: 1080px) { .quality-toolbar { grid-template-columns: 1fr 1fr; } .project-field { grid-column: 1 / -1; } .quality-layout { grid-template-columns: 1fr; } .task-panel { position: static; } }
@media (max-width: 700px) { .quality-toolbar { grid-template-columns: 1fr; } .project-field { grid-column: auto; } .metrics-strip, .cleaning-summary { grid-template-columns: repeat(2, 1fr); } .compact-head { align-items: stretch; flex-direction: column; gap: 10px; } .issue-filters { flex-direction: column; } .issue-filters .el-select { width: 100%; } .duplicate-field, .duplicate-cleaning { grid-template-columns: 1fr; } .original-export { align-items: flex-start; flex-direction: column; } }
</style>
