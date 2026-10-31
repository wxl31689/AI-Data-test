<script setup lang="ts">
import { Connection, FolderOpened, Guide, Loading } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { apiClient, type ApiEnvelope } from '../api/client'
import { listProjects } from '../api/projects'

interface HealthStatus {
  status: 'UP' | 'DEGRADED'
  service: string
  version: string
  database: 'UP' | 'DOWN' | 'DEGRADED'
  checkedAt: string
}

const loading = ref(true)
const health = ref<HealthStatus | null>(null)
const projectCount = ref<number | null>(null)
const backendError = ref(false)

const healthLabel = computed(() => {
  if (backendError.value) return '未连接'
  if (!health.value) return '检查中'
  return health.value.status === 'UP' ? '运行正常' : '部分降级'
})

const healthType = computed(() => {
  if (backendError.value) return 'danger'
  return health.value?.status === 'UP' ? 'success' : 'warning'
})

async function loadOverview() {
  loading.value = true
  backendError.value = false

  try {
    const [healthResponse, projects] = await Promise.all([
      apiClient.get<ApiEnvelope<HealthStatus>>('/health'),
      listProjects(),
    ])
    health.value = healthResponse.data.data
    projectCount.value = projects.length
  } catch {
    backendError.value = true
    health.value = null
    projectCount.value = null
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
</script>

<template>
  <div class="dashboard">
    <section class="hero-panel">
      <div>
        <span class="hero-kicker">AI Agent 闭环已接入</span>
        <h2>从自动化测试走向可控、可解释的智能分析</h2>
        <p>
          当前版本在批量执行、数据质量和报告基础上，加入结构化用例生成、人工审核、白名单工具调用和真实失败证据分析。
        </p>
      </div>
      <el-button type="primary" size="large" @click="$router.push('/agent')">
        打开 Agent 中心
      </el-button>
    </section>

    <section class="metrics-grid" v-loading="loading">
      <article class="metric-card">
        <div class="metric-icon teal"><Connection /></div>
        <div>
          <span>后端服务</span>
          <strong>{{ healthLabel }}</strong>
        </div>
        <el-tag :type="healthType" effect="light" round>{{ health?.version ?? '0.1.0' }}</el-tag>
      </article>

      <article class="metric-card">
        <div class="metric-icon blue"><FolderOpened /></div>
        <div>
          <span>真实项目数</span>
          <strong>{{ projectCount ?? '--' }}</strong>
        </div>
        <small>来自数据库</small>
      </article>

      <article class="metric-card">
        <div class="metric-icon amber"><Loading /></div>
        <div>
          <span>数据库状态</span>
          <strong>{{ health?.database ?? '--' }}</strong>
        </div>
        <small>{{ health ? '最近已检查' : '等待后端连接' }}</small>
      </article>
    </section>

    <section class="work-grid">
      <article class="panel milestone-panel">
        <div class="panel-head">
          <div>
            <span class="section-label">第 1 周</span>
            <h3>基础接口测试闭环</h3>
          </div>
          <el-tag type="success" effect="plain">已完成</el-tag>
        </div>

        <div class="milestone-list">
          <div class="milestone done">
            <span>01</span>
            <div><strong>工程初始化</strong><p>前后端、数据库配置、统一响应</p></div>
          </div>
          <div class="milestone done">
            <span>02</span>
            <div><strong>项目与接口管理</strong><p>项目 CRUD、OpenAPI 导入与接口查询</p></div>
          </div>
          <div class="milestone done">
            <span>03</span>
            <div><strong>车辆演示 API</strong><p>固定数据、异常边界与 OpenAPI 契约</p></div>
          </div>
          <div class="milestone done">
            <span>04</span>
            <div><strong>测试用例管理</strong><p>请求配置、断言与审核状态</p></div>
          </div>
          <div class="milestone done">
            <span>05</span>
            <div><strong>真实请求执行</strong><p>HTTP 请求、断言和结果持久化</p></div>
          </div>
        </div>
      </article>

      <article class="panel principle-panel">
        <div class="guide-icon"><Guide /></div>
        <span class="section-label">核心原则</span>
        <h3>事实由规则引擎判断</h3>
        <p>PASS、FAIL 和 ERROR 来自可复现的断言结果。Agent 只生成候选方案和解释真实证据，不替代确定性判断。</p>
        <div class="principle-foot">
          <span></span>
          可控 · 可解释 · 可追溯
        </div>
      </article>
    </section>
  </div>
</template>

