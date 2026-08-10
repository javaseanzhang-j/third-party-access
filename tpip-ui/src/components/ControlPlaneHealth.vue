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
  if (health.isPending.value) return { tone: 'checking', label: 'CHECKING', hint: '正在检查 Control Plane' }
  if (health.isSuccess.value && health.data.value?.status === 'UP') {
    return { tone: 'online', label: 'ONLINE', hint: 'Control Plane 正常' }
  }
  return { tone: 'offline', label: 'OFFLINE', hint: 'Control Plane 不可用，点击重试' }
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
    <span /> CONTROL {{ state.label }}
  </button>
</template>
