<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { formatTime } from '@/features/global-impact/model/presentation'
import { governancePolicyAssetApi, type PolicyFilters, type PolicyScope,
  type PolicyStatus } from '../api/governancePolicyAssetApi'

const router = useRouter()
const draft = reactive<{ scope: PolicyScope | ''; status: PolicyStatus | ''; keyword: string }>({
  scope: '', status: '', keyword: ''
})
const applied = ref<PolicyFilters>({ page: 0, size: 20 })
const queryKey = computed(() => ['governance-policy-assets', applied.value])
const policies = useQuery({ queryKey, queryFn: ({ signal }) => governancePolicyAssetApi.policies(applied.value, signal) })
function search(): void { applied.value = { ...draft, page: 0, size: 20 } }
function reset(): void { draft.scope = ''; draft.status = ''; draft.keyword = ''; search() }
function changePage(page: number): void { applied.value = { ...applied.value, page: page - 1 } }
function statusType(status: PolicyStatus): 'success' | 'warning' | 'info' {
  return status === 'ACTIVE' ? 'success' : status === 'PAUSED' ? 'warning' : 'info'
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div><h2>治理策略资产</h2><p>浏览 Global 与 Workspace 治理策略、不可变版本及其影响任务使用关系。</p></div>
      <el-button :loading="policies.isFetching.value" @click="policies.refetch()">刷新资产</el-button>
    </div>
    <div class="surface">
      <div class="policy-filter">
        <el-input v-model="draft.keyword" clearable placeholder="策略编码、名称或 Workspace" @keyup.enter="search" />
        <el-select v-model="draft.scope" clearable placeholder="全部作用域">
          <el-option label="Global" value="GLOBAL" /><el-option label="Workspace" value="WORKSPACE" />
        </el-select>
        <el-select v-model="draft.status" clearable placeholder="全部状态">
          <el-option label="草稿" value="DRAFT" /><el-option label="已暂停" value="PAUSED" /><el-option label="生效中" value="ACTIVE" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
      </div>
      <AsyncStatePanel :loading="policies.isPending.value" :error="policies.isError.value"
        loading-label="正在加载策略资产" error-title="策略资产读取失败" @retry="policies.refetch()">
        <template v-if="policies.data.value">
          <el-empty v-if="!policies.data.value.items.length" description="当前筛选条件下没有策略资产" />
          <el-table v-else :data="policies.data.value.items" row-key="policyId">
            <el-table-column label="策略资产" min-width="260"><template #default="{ row }">
              <button class="link-button" @click="router.push(`/governance-policies/${row.policyId}`)">{{ row.policyName }}</button>
              <div class="subtle mono" style="margin-top:4px;font-size:10px">{{ row.policyCode }}</div>
            </template></el-table-column>
            <el-table-column label="作用域" width="130"><template #default="{ row }">
              <el-tag effect="plain">{{ row.scope }}</el-tag>
              <div v-if="row.workspace" class="subtle" style="margin-top:5px;font-size:10px">{{ row.workspace.workspaceName }} · {{ row.workspace.environmentCode }}</div>
            </template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column label="当前版本" width="105"><template #default="{ row }">
              <button v-if="row.currentVersion" class="link-button" @click="router.push(`/governance-policies/${row.policyId}/versions/${row.currentVersion.versionId}`)">v{{ row.currentVersion.versionNo }}</button><span v-else>—</span>
            </template></el-table-column>
            <el-table-column label="版本 / 任务" width="115"><template #default="{ row }">{{ row.versionCount }} / {{ row.impactJobCount }}</template></el-table-column>
            <el-table-column label="最近更新" width="175"><template #default="{ row }">{{ formatTime(row.updatedAt) }}</template></el-table-column>
            <el-table-column label="操作" width="80" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="router.push(`/governance-policies/${row.policyId}`)">追溯</el-button></template></el-table-column>
          </el-table>
          <div class="policy-pagination"><span>共 {{ policies.data.value.totalElements }} 项资产</span>
            <el-pagination background layout="prev, pager, next" :page-size="policies.data.value.size"
              :total="policies.data.value.totalElements" :current-page="policies.data.value.page + 1" @current-change="changePage" />
          </div>
        </template>
      </AsyncStatePanel>
    </div>
  </section>
</template>
