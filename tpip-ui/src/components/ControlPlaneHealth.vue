<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { systemApi } from '@/api/systemApi'

const health = useQuery({
  queryKey: ['control-plane-health'],
  queryFn: ({ signal }) => systemApi.health(signal),
  retry: false,
  refetchInterval: 15_000
})
const state = computed(() => {
  if (health.isPending.value) return { tone: 'checking', label: '检查中', hint: '正在检查控制服务' }
  if (health.isSuccess.value && health.data.value?.status === 'UP') {
    return { tone: 'online', label: '正常', hint: '控制服务正常' }
  }
  return { tone: 'offline', label: '离线', hint: '控制服务不可用，点击重试' }
})
</script>

<template>
  <button
    class="health-pill"
    :class="`health-pill--${state.tone}`"
    type="button"
    :title="state.hint"
    :aria-label="state.hint"
    @click="health.refetch()"
  >
    <span /> 控制服务 {{ state.label }}
  </button>
</template>
