<script setup lang="ts">
import {
  DataAnalysis,
  DocumentChecked,
  Files,
  Histogram,
  MagicStick,
  Operation,
  SetUp,
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import programmerAvatar from './assets/programmer-avatar.svg'

const route = useRoute()
const pageTitle = computed(() => String(route.meta.title ?? 'AI Agent'))
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">
          <img :src="programmerAvatar" alt="程序员头像" width="48" height="48" />
        </div>
        <div>
          <strong>AI Agent</strong>
          <span>智能测试平台</span>
        </div>
      </div>

      <el-menu :default-active="route.path" router class="nav-menu">
        <el-menu-item index="/">
          <el-icon><DataAnalysis /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/projects">
          <el-icon><Files /></el-icon>
          <span>项目与接口</span>
        </el-menu-item>
        <el-menu-item index="/cases">
          <el-icon><DocumentChecked /></el-icon>
          <span>测试用例</span>
        </el-menu-item>
        <el-menu-item index="/runs">
          <el-icon><Operation /></el-icon>
          <span>测试执行</span>
        </el-menu-item>
        <el-menu-item index="/data-quality">
          <el-icon><SetUp /></el-icon>
          <span>数据质量</span>
        </el-menu-item>
        <el-menu-item index="/agent">
          <el-icon><MagicStick /></el-icon>
          <span>AI Agent</span>
        </el-menu-item>
        <el-menu-item index="/reports">
          <el-icon><Histogram /></el-icon>
          <span>报告与历史</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-foot">
        <span class="status-dot"></span>
        初始开发版本 0.1.0
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div>
          <span class="eyebrow">AI 辅助接口测试与数据质量分析平台</span>
          <h1>{{ pageTitle }}</h1>
        </div>
        <el-tag type="info" effect="plain" round>开发环境</el-tag>
      </header>

      <section class="page-content">
        <router-view />
      </section>
    </main>
  </div>
</template>

