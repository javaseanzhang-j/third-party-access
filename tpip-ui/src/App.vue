<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ControlPlaneHealth from '@/components/ControlPlaneHealth.vue'

const route = useRoute()
const activeMenu = computed(() => {
  if (route.path.startsWith('/integration-assets/wizard')) return '/integration-assets/wizard'
  if (route.path.startsWith('/integration-assets/provider-access')) return '/integration-assets/provider-access'
  if (route.path.startsWith('/integration-assets/advanced/provider-assets')) return '/integration-assets/advanced/provider-assets'
  if (route.path.startsWith('/integration-assets/channels')) return '/integration-assets/channels'
  if (route.path.startsWith('/integration-assets/interfaces')) return '/integration-assets/interfaces'
  if (route.path.startsWith('/integration-assets/services')) return '/integration-assets/services'
  if (route.path.startsWith('/integration-assets/consumers')) return '/integration-assets/consumers'
  if (route.path.startsWith('/integration-assets/provider-contract-versions')) return '/integration-assets/provider-contract-versions'
  if (route.path.startsWith('/integration-assets/mappings')) return '/integration-assets/mappings'
  if (route.path.startsWith('/integration-assets/policies')) return '/integration-assets/policies'
  if (route.path.startsWith('/integration-assets/binding-versions')) return '/integration-assets/binding-versions'
  if (route.path.startsWith('/integration-assets/fixture-suites')) return '/integration-assets/fixture-suites'
  if (route.path.startsWith('/integration-assets/workspaces')) return '/integration-assets/workspaces'
  if (route.path.startsWith('/integration-assets/releases')) return '/integration-assets/releases'
  if (route.path.startsWith('/integration-assets/deployments')) return '/integration-assets/deployments'
  if (route.path.startsWith('/integration-assets/runtime-invoke')) return '/integration-assets/runtime-invoke'
  if (route.path.startsWith('/integration-assets/canonical')) return '/integration-assets/canonical'
  if (route.path.startsWith('/integration-assets')) return '/integration-assets'
  if (route.path.startsWith('/global-impact-jobs')) return '/global-impact-jobs'
  if (route.path.startsWith('/governance-policies')) return '/governance-policies'
  if (route.path.startsWith('/workspaces')) return '/workspaces'
  if (route.path.startsWith('/drift-workbench')) return '/drift-workbench'
  if (route.path.startsWith('/drift-operations')) return '/drift-operations'
  if (route.path.startsWith('/drift-governance-metrics')) return '/drift-governance-metrics'
  if (route.path.startsWith('/drift-governance-evaluations')) return '/drift-governance-evaluations'
  if (route.path.startsWith('/drift-reminder-batches')) return '/drift-reminder-batches'
  return route.path
})
const pageTitle = computed(() => String(route.meta.title ?? 'TPIP 工作台'))
</script>

<template>
  <div class="app-shell">
    <aside class="side-panel" aria-label="主导航">
      <div class="brand-block">
        <div class="brand-mark">TP</div>
        <div>
          <strong>TPIP</strong>
          <span>第三方接入平台</span>
        </div>
      </div>
      <el-menu :default-active="activeMenu" router class="side-menu">
        <el-sub-menu index="access-configuration">
          <template #title><span class="menu-dot" /><span>第三方接入</span></template>
          <el-menu-item index="/integration-assets/provider-access">接入工作区</el-menu-item>
          <el-menu-item index="/integration-assets">接入总览</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="service-management">
          <template #title><span class="menu-dot menu-dot--service" /><span>服务管理</span></template>
          <el-menu-item index="/integration-assets/services">接入服务</el-menu-item>
          <el-menu-item index="/integration-assets/consumers">调用方管理</el-menu-item>
          <el-menu-item index="/integration-assets/runtime-invoke">统一调用控制台</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="verification-release">
          <template #title><span class="menu-dot menu-dot--verification" /><span>验证与发布</span></template>
          <el-menu-item index="/integration-assets/wizard">测试与发布向导</el-menu-item>
          <el-menu-item index="/integration-assets/workspaces">配置验证空间</el-menu-item>
          <el-menu-item index="/integration-assets/releases">评审与发布包</el-menu-item>
          <el-menu-item index="/integration-assets/deployments">运行版本</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="advanced-assets">
          <template #title><span class="menu-dot menu-dot--advanced" /><span>高级管理</span></template>
          <el-menu-item index="/integration-assets/advanced/provider-assets">第三方技术资产</el-menu-item>
          <el-menu-item index="/integration-assets/channels">通道参数与规则</el-menu-item>
          <el-menu-item index="/integration-assets/interfaces">接口通道关系</el-menu-item>
          <el-menu-item index="/integration-assets/canonical">标准契约资产</el-menu-item>
          <el-menu-item index="/integration-assets/provider-contract-versions">第三方报文结构</el-menu-item>
          <el-menu-item index="/integration-assets/mappings">JSONPath 字段映射</el-menu-item>
          <el-menu-item index="/integration-assets/policies">执行规则</el-menu-item>
          <el-menu-item index="/integration-assets/binding-versions">可执行配置版本</el-menu-item>
          <el-menu-item index="/integration-assets/fixture-suites">验证用例集</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="operations-governance">
          <template #title><span class="menu-dot menu-dot--governance" /><span>运营治理</span></template>
          <el-menu-item index="/workspaces">验证空间资产</el-menu-item>
          <el-menu-item index="/governance-policies">治理策略资产</el-menu-item>
          <el-menu-item index="/drift-workbench">漂移治理工作台</el-menu-item>
          <el-menu-item index="/drift-operations">治理操作证据</el-menu-item>
          <el-menu-item index="/drift-governance-metrics">治理运营度量</el-menu-item>
          <el-menu-item index="/drift-governance-evaluations">治理评估资产</el-menu-item>
          <el-menu-item index="/drift-reminder-batches">提醒批次资产</el-menu-item>
          <el-menu-item index="/global-impact-operations">全局运营态势</el-menu-item>
          <el-menu-item index="/global-impact-jobs">全局影响任务</el-menu-item>
        </el-sub-menu>
      </el-menu>
      <div class="local-mode">
        <span class="local-pulse" />
        <div><strong>本地单用户</strong><small>无需登录 · Loopback</small></div>
      </div>
    </aside>
    <main class="main-panel">
      <header class="top-bar">
        <div>
          <span class="eyebrow">第三方接入平台</span>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="top-actions">
          <ControlPlaneHealth />
          <div class="environment-pill"><span /> 本地环境</div>
        </div>
      </header>
      <div class="content-panel"><router-view /></div>
    </main>
  </div>
</template>
