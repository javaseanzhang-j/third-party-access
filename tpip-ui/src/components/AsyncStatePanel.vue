<script setup lang="ts">
withDefaults(defineProps<{
  loading?: boolean
  error?: boolean
  empty?: boolean
  loadingLabel?: string
  errorTitle?: string
  emptyDescription?: string
}>(), {
  loading: false,
  error: false,
  empty: false,
  loadingLabel: '正在读取数据',
  errorTitle: '数据读取失败，请稍后重试。',
  emptyDescription: '暂无数据'
})

defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="async-state" role="status" :aria-label="loadingLabel">
    <el-skeleton animated :rows="4" />
  </div>
  <div v-else-if="error" class="async-state async-state--error" role="alert">
    <el-alert :title="errorTitle" type="error" :closable="false" show-icon />
    <el-button type="primary" plain @click="$emit('retry')">重新加载</el-button>
  </div>
  <div v-else-if="empty" class="async-state" role="status">
    <el-empty :description="emptyDescription" />
  </div>
  <slot v-else />
</template>
