import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import DataQualityView from '../views/DataQualityView.vue'
import ProjectsView from '../views/ProjectsView.vue'
import ReportsView from '../views/ReportsView.vue'
import RunsView from '../views/RunsView.vue'
import TestCasesView from '../views/TestCasesView.vue'
import AgentView from '../views/AgentView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: DashboardView, meta: { title: '工作台' } },
    { path: '/projects', component: ProjectsView, meta: { title: '项目与接口' } },
    {
      path: '/cases',
      component: TestCasesView,
      meta: { title: '测试用例' },
    },
    {
      path: '/runs',
      component: RunsView,
      meta: { title: '测试执行' },
    },
    {
      path: '/data-quality',
      component: DataQualityView,
      meta: { title: '数据质量' },
    },
    {
      path: '/agent',
      component: AgentView,
      meta: { title: 'AI Agent 中心' },
    },
    {
      path: '/reports',
      component: ReportsView,
      meta: { title: '报告与历史' },
    },
  ],
})

export default router

