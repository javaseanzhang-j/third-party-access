<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { ApiError } from '@/api/http'
import { globalImpactCreationApi, type CreateGlobalImpactJobInput } from '../api/globalImpactCreationApi'
import { maximumTtlSeconds, minimumTtlSeconds, validateCreateJobInput } from '../model/createJobValidation'

const router = useRouter(); const queryClient = useQueryClient()
const form = reactive<{ policyId: number | null; versionId: number | null; ttlSeconds: number }>({
  policyId: null, versionId: null, ttlSeconds: 3600
})
const validationError = ref('')
const policies = useQuery({
  queryKey: ['drift-governance-policies', 'global'],
  queryFn: ({ signal }) => globalImpactCreationApi.policies(signal),
  select: values => values.filter(value => value.scope === 'GLOBAL')
})
const versions = useQuery({
  queryKey: computed(() => ['drift-governance-policy-versions', form.policyId]),
  queryFn: ({ signal }) => globalImpactCreationApi.versions(form.policyId as number, signal),
  enabled: computed(() => form.policyId !== null),
  select: values => values.filter(value => ['DRAFT', 'PUBLISHED'].includes(value.lifecycleStatus))
})
const selectedPolicy = computed(() => policies.data.value?.find(value => value.id === form.policyId) ?? null)
const selectedVersion = computed(() => versions.data.value?.find(value => value.id === form.versionId) ?? null)
const ttlLabel = computed(() => {
  if (form.ttlSeconds < 3600) return `${Math.round(form.ttlSeconds / 60)} 分钟`
  return `${form.ttlSeconds / 3600} 小时`
})

watch(() => form.policyId, () => { form.versionId = null; validationError.value = '' })

const createJob = useMutation({
  mutationFn: (input: CreateGlobalImpactJobInput) => globalImpactCreationApi.create(input),
  onSuccess: job => {
    void queryClient.invalidateQueries({ queryKey: ['global-impact-jobs'] })
    void router.push(`/global-impact-jobs/${encodeURIComponent(job.jobId)}`)
  },
  onError: error => {
    validationError.value = error instanceof ApiError
      ? `创建失败：${error.message}` : '创建失败，请确认 Control Plane 与本地数据库状态。'
  }
})

function submit(): void {
  const input = {
    candidatePolicyId: form.policyId ?? undefined,
    candidateVersionId: form.versionId ?? undefined,
    ttlSeconds: form.ttlSeconds
  }
  const error = validateCreateJobInput(input)
  if (error) { validationError.value = error; return }
  validationError.value = ''
  createJob.mutate(input as CreateGlobalImpactJobInput)
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div><el-button link @click="router.push('/global-impact-jobs')">← 返回任务列表</el-button><h2 style="margin-top:10px">创建全局影响任务</h2><p>冻结当前 Workspace 覆盖，生成可追踪、可恢复的策略影响计算任务。</p></div>
    </div>
    <div class="creation-layout">
      <div class="surface creation-form">
        <h3>候选策略与证据窗口</h3>
        <AsyncStatePanel :loading="policies.isPending.value" :error="policies.isError.value"
          :empty="policies.isSuccess.value && !policies.data.value?.length"
          loading-label="正在加载 Global 策略" error-title="Global 策略读取失败"
          empty-description="当前没有 Global 治理策略，请先通过策略资产 API 创建。" @retry="policies.refetch()">
          <el-form label-position="top" @submit.prevent="submit">
            <el-form-item label="Global 治理策略" required>
              <el-select v-model="form.policyId" filterable placeholder="选择候选策略" style="width:100%">
                <el-option v-for="policy in policies.data.value ?? []" :key="policy.id"
                  :label="`${policy.policyName} · ${policy.policyCode}`" :value="policy.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="候选版本" required>
              <el-select v-model="form.versionId" :disabled="!form.policyId" :loading="versions.isFetching.value"
                placeholder="选择 DRAFT 或 PUBLISHED 版本" style="width:100%">
                <el-option v-for="version in versions.data.value ?? []" :key="version.id"
                  :label="`v${version.versionNo} · ${version.lifecycleStatus}`" :value="version.id" />
              </el-select>
              <span v-if="form.policyId && versions.isSuccess.value && !versions.data.value?.length" class="form-hint form-hint--danger">该策略没有可计算的 DRAFT/PUBLISHED 版本。</span>
            </el-form-item>
            <el-form-item label="证据有效期" required>
              <div class="ttl-row">
                <el-input-number v-model="form.ttlSeconds" :min="minimumTtlSeconds" :max="maximumTtlSeconds"
                  :step="300" controls-position="right" />
                <span>秒 · {{ ttlLabel }}</span>
              </div>
              <div class="ttl-presets">
                <el-button v-for="item in [{ label: '30 分钟', value: 1800 }, { label: '1 小时', value: 3600 }, { label: '4 小时', value: 14400 }, { label: '24 小时', value: 86400 }]"
                  :key="item.value" size="small" :type="form.ttlSeconds === item.value ? 'primary' : 'default'"
                  @click="form.ttlSeconds = item.value">{{ item.label }}</el-button>
              </div>
            </el-form-item>
            <p v-if="validationError" class="command-validation" role="alert">{{ validationError }}</p>
            <div class="creation-actions">
              <el-button :disabled="createJob.isPending.value" @click="router.push('/global-impact-jobs')">取消</el-button>
              <el-button type="primary" native-type="submit" :loading="createJob.isPending.value">创建影响任务</el-button>
            </div>
          </el-form>
        </AsyncStatePanel>
      </div>
      <aside class="surface creation-review">
        <h3>创建前确认</h3>
        <div class="review-item"><span>候选策略</span><strong>{{ selectedPolicy?.policyName ?? '尚未选择' }}</strong><small>{{ selectedPolicy?.policyCode ?? '—' }}</small></div>
        <div class="review-item"><span>候选版本</span><strong>{{ selectedVersion ? `v${selectedVersion.versionNo}` : '尚未选择' }}</strong><small>{{ selectedVersion?.contentChecksum ?? '—' }}</small></div>
        <div class="review-item"><span>证据窗口</span><strong>{{ ttlLabel }}</strong><small>到期后任务不能继续计算、重试或封板</small></div>
        <el-alert title="创建时会冻结当前全部 Workspace 及其策略基线。创建本身不会启动 Worker、自动封板、发布或激活策略。"
          type="warning" :closable="false" show-icon />
      </aside>
    </div>
  </section>
</template>
