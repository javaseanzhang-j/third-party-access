<script setup lang="ts">
import { computed } from 'vue'
import type { JobDetail, WorkspaceImpactSummary } from '../api/globalImpactApi'
import { sealChecks } from '../model/jobOperations'
import { formatTime } from '../model/presentation'

const props = defineProps<{ detail: JobDetail; summary: WorkspaceImpactSummary; submitting: boolean }>()
const emit = defineEmits<{ seal: [] }>()
const checks = computed(() => sealChecks(props.detail, props.summary))
const passed = computed(() => checks.value.every(check => check.passed))
</script>

<template>
  <div class="surface seal-preflight">
    <div class="seal-preflight__heading">
      <div><span>READY 封板前检查</span><h3>{{ passed ? '全部前置条件已满足' : '仍有条件未满足' }}</h3></div>
      <el-button type="primary" :disabled="!passed" :loading="submitting" @click="emit('seal')">封板不可变快照</el-button>
    </div>
    <div class="seal-checks">
      <div v-for="check in checks" :key="check.code" :class="{ 'seal-check--passed': check.passed }">
        <span>{{ check.passed ? '✓' : '×' }}</span>{{ check.label }}
      </div>
    </div>
    <div class="seal-preflight__facts">
      <span>Workspace <b>{{ summary.workspaceCount }}</b></span>
      <span>变化报告 <b>{{ summary.impactTotals.changedReports }}</b></span>
      <span>过期时间 <b>{{ formatTime(detail.summary.expiresAt) }}</b></span>
      <span>Row Version <b>{{ detail.summary.rowVersion }}</b></span>
    </div>
  </div>
</template>
