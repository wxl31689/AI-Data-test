<script setup lang="ts">
import { Plus, Refresh, UploadFilled, View } from '@element-plus/icons-vue'
import {
  ElMessage,
  type FormInstance,
  type FormRules,
  type UploadFile,
  type UploadInstance,
} from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import {
  importOpenApi,
  listApiDefinitions,
  type ApiDefinition,
} from '../api/apiDefinitions'
import { createProject, listProjects, type Project } from '../api/projects'

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const definitionsLoading = ref(false)
const dialogVisible = ref(false)
const importDialogVisible = ref(false)
const projects = ref<Project[]>([])
const definitions = ref<ApiDefinition[]>([])
const selectedProject = ref<Project | null>(null)
const importTarget = ref<Project | null>(null)
const selectedFile = ref<File | null>(null)
const formRef = ref<FormInstance>()
const uploadRef = ref<UploadInstance>()
const form = reactive({ name: '', description: '' })
const rules: FormRules = {
  name: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { max: 120, message: '最多 120 个字符', trigger: 'blur' },
  ],
  description: [{ max: 1000, message: '最多 1000 个字符', trigger: 'blur' }],
}

async function loadProjects() {
  loading.value = true
  try {
    projects.value = await listProjects()
  } catch {
    ElMessage.error('无法加载项目，请确认后端服务已启动')
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

async function submitProject() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await createProject({ name: form.name.trim(), description: form.description.trim() || undefined })
    ElMessage.success('项目创建成功')
    dialogVisible.value = false
    await loadProjects()
  } catch {
    ElMessage.error('项目创建失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function viewDefinitions(project: Project) {
  selectedProject.value = project
  definitionsLoading.value = true
  try {
    definitions.value = await listApiDefinitions(project.id)
  } catch {
    definitions.value = []
    ElMessage.error('接口定义加载失败')
  } finally {
    definitionsLoading.value = false
  }
}

function openImportDialog(project: Project) {
  importTarget.value = project
  selectedFile.value = null
  uploadRef.value?.clearFiles()
  importDialogVisible.value = true
}

function handleFileChange(file: UploadFile) {
  selectedFile.value = file.raw ?? null
}

function handleFileRemove() {
  selectedFile.value = null
}

async function submitImport() {
  if (!importTarget.value || !selectedFile.value) {
    ElMessage.warning('请选择 OpenAPI JSON 文件')
    return
  }

  importing.value = true
  try {
    const result = await importOpenApi(importTarget.value.id, selectedFile.value)
    ElMessage.success(`导入完成：新增 ${result.importedCount} 个，更新 ${result.updatedCount} 个接口`)
    if (result.warnings.length > 0) {
      ElMessage.warning(result.warnings[0])
    }
    importDialogVisible.value = false
    await viewDefinitions(importTarget.value)
  } catch {
    ElMessage.error('导入失败，请确认文件为 OpenAPI 3.x JSON')
  } finally {
    importing.value = false
  }
}

function methodTagType(method: string) {
  const mapping: Record<string, 'success' | 'primary' | 'warning' | 'danger' | 'info'> = {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    PATCH: 'warning',
    DELETE: 'danger',
  }
  return mapping[method] ?? 'info'
}

onMounted(loadProjects)
</script>

<template>
  <div class="projects-page">
    <section class="page-intro">
      <div>
        <span class="section-label">FR-01 项目与接口管理</span>
        <h2>管理测试范围与已登记目标</h2>
        <p>项目是接口定义、测试用例与执行历史的边界。只有登记并审核的目标才允许进入后续测试执行。</p>
      </div>
      <div class="action-row">
        <el-button :icon="Refresh" @click="loadProjects">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建项目</el-button>
      </div>
    </section>

    <section class="panel table-panel">
      <el-table v-if="projects.length || loading" :data="projects" v-loading="loading" stripe>
        <el-table-column prop="name" label="项目名称" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.description || '暂无描述' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" effect="light">
              {{ scope.row.status === 'ACTIVE' ? '启用' : '已归档' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="185">
          <template #default="scope">{{ new Date(scope.row.createdAt).toLocaleString('zh-CN') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :icon="View" @click="viewDefinitions(scope.row)">查看接口</el-button>
            <el-button link type="primary" :icon="UploadFilled" @click="openImportDialog(scope.row)">导入</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else description="还没有项目，创建一个项目开始登记测试接口">
        <el-button type="primary" @click="openCreateDialog">创建第一个项目</el-button>
      </el-empty>
    </section>

    <section v-if="selectedProject" class="panel definitions-panel">
      <div class="panel-head definitions-head">
        <div>
          <span class="section-label">已登记接口</span>
          <h3>{{ selectedProject.name }}</h3>
        </div>
        <el-button type="primary" plain :icon="UploadFilled" @click="openImportDialog(selectedProject)">
          导入 OpenAPI JSON
        </el-button>
      </div>

      <el-table v-if="definitions.length || definitionsLoading" :data="definitions" v-loading="definitionsLoading" stripe>
        <el-table-column label="方法" width="100" align="center">
          <template #default="scope">
            <el-tag :type="methodTagType(scope.row.method)" effect="light">{{ scope.row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="220">
          <template #default="scope"><code>{{ scope.row.path }}</code></template>
        </el-table-column>
        <el-table-column prop="name" label="接口名称" min-width="180" />
        <el-table-column prop="summary" label="说明" min-width="220" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.summary || '未提供摘要' }}</template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="基础地址" min-width="190" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.baseUrl || '待配置' }}</template>
        </el-table-column>
      </el-table>

      <el-empty v-else description="该项目还没有接口定义">
        <el-button type="primary" @click="openImportDialog(selectedProject)">导入 OpenAPI JSON</el-button>
      </el-empty>
    </section>

    <el-dialog v-model="dialogVisible" title="新建测试项目" width="min(520px, 92vw)">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" maxlength="120" show-word-limit placeholder="例如：车辆业务接口回归" />
        </el-form-item>
        <el-form-item label="项目描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="说明测试目标、环境和授权范围"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitProject">确认创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialogVisible" title="导入 OpenAPI 3.x JSON" width="min(580px, 92vw)">
      <p class="dialog-intro">
        目标项目：<strong>{{ importTarget?.name }}</strong>。同一路径和方法再次导入时会更新已有定义，不会重复创建。
      </p>
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".json,application/json"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖放文件到这里，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 OpenAPI 3.x JSON，文件不超过 2 MB</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.definitions-panel {
  margin-top: 22px;
  padding: 26px;
}

.definitions-head {
  margin-bottom: 22px;
}

.definitions-head h3 {
  margin: 7px 0 0;
  color: var(--app-text-primary);
}

code {
  padding: 4px 7px;
  border-radius: 6px;
  color: var(--app-accent-strong);
  background: var(--app-accent-soft);
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 12px;
}

.dialog-intro {
  margin: 0 0 18px;
  color: var(--app-text-secondary);
  line-height: 1.65;
}
</style>
