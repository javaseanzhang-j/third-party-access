<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { canonicalAssetApi } from '../api/canonicalAssetApi'
import { accessChannelApi } from '../api/accessChannelApi'
import { integrationAssetApi } from '../api/integrationAssetApi'
import { mappingAssetApi } from '../api/mappingAssetApi'
import { buildAccessServices, buildThirdPartyInterfaces } from '../model/productModel'

const router = useRouter()
const providers = useQuery({ queryKey: ['product-providers'], queryFn: ({ signal }) => integrationAssetApi.providers('', signal) })
const channels = useQuery({ queryKey: ['access-channels'], queryFn: ({ signal }) => accessChannelApi.channels(undefined, signal) })
const contracts = useQuery({ queryKey: ['product-contracts'], queryFn: ({ signal }) => integrationAssetApi.contracts(undefined, signal) })
const endpoints = useQuery({ queryKey: ['product-endpoints'], queryFn: ({ signal }) => integrationAssetApi.endpoints(undefined, signal) })
const operations = useQuery({ queryKey: ['product-services'], queryFn: ({ signal }) => canonicalAssetApi.operations(undefined, signal) })
const bindings = useQuery({ queryKey: ['product-service-targets'], queryFn: ({ signal }) => mappingAssetApi.bindings(signal) })
const queries = [providers, channels, contracts, endpoints, operations, bindings]
const loading = computed(() => queries.some(item => item.isPending.value))
const hasError = computed(() => queries.some(item => item.isError.value))
const interfaces = computed(() => buildThirdPartyInterfaces(providers.data.value?.items ?? [],
  contracts.data.value?.items ?? [], endpoints.data.value?.items ?? []))
const services = computed(() => buildAccessServices(operations.data.value?.items ?? [], bindings.data.value?.items ?? []))
function refresh(): void { queries.forEach(item => { void item.refetch() }) }
</script>

<template>
  <section>
    <div class="integration-hero integration-hero--product surface">
      <div>
        <span>THIRD-PARTY ACCESS</span>
        <h2>把一个第三方能力，配置成业务可稳定调用的服务</h2>
        <p>先配置第三方系统、通道和接口，再用 serviceCode 绑定一个或多个厂商实现。平台负责公共参数、字段转换、路由选择和请求组装。</p>
        <div class="action-row">
          <el-button type="primary" @click="router.push('/integration-assets/provider-access')">＋ 新增第三方系统</el-button>
          <el-button @click="router.push('/integration-assets/services')">查看接入服务</el-button>
        </div>
      </div>
      <div class="integration-hero__principle">
        <small>对业务系统保持稳定</small>
        <strong>统一入口 + serviceCode</strong>
        <p>serviceCode 表示服务，不包含厂商身份；同一服务可以由多个第三方共同承载。</p>
      </div>
    </div>

    <el-alert v-if="hasError" class="command-notice" type="error" :closable="false"
      title="部分配置读取失败，请确认 Control Plane 已启动。" show-icon />

    <div class="metric-strip integration-metrics" :class="{ 'is-loading': loading }">
      <div class="metric"><span>第三方系统</span><strong>{{ providers.data.value?.totalElements ?? '—' }}</strong><small>能力提供方</small></div>
      <div class="metric"><span>接入通道</span><strong>{{ channels.data.value?.length ?? '—' }}</strong><small>地址、账号与公共规则</small></div>
      <div class="metric"><span>第三方接口</span><strong>{{ loading ? '—' : interfaces.length }}</strong><small>Method、Path 与原生协议</small></div>
      <div class="metric"><span>接入服务</span><strong>{{ loading ? '—' : services.length }}</strong><small>业务调用的 serviceCode</small></div>
    </div>

    <div class="product-journey surface">
      <div class="section-title"><div><span class="eyebrow">RECOMMENDED FLOW</span><h3>从第三方接口到稳定服务</h3></div><el-button :loading="loading" @click="refresh">刷新</el-button></div>
      <div class="product-journey__steps">
        <button type="button" @click="router.push('/integration-assets/provider-access')"><b>1</b><span><strong>第三方系统</strong><small>先说明能力由谁提供</small></span></button>
        <i>→</i>
        <button type="button" @click="router.push('/integration-assets/channels')"><b>2</b><span><strong>接入通道</strong><small>配置 baseUrl、凭据和公共规则</small></span></button>
        <i>→</i>
        <button type="button" @click="router.push('/integration-assets/interfaces')"><b>3</b><span><strong>第三方接口</strong><small>配置 Method、Path 和接口参数</small></span></button>
        <i>→</i>
        <button type="button" @click="router.push('/integration-assets/services')"><b>4</b><span><strong>接入服务</strong><small>定义 serviceCode 和标准报文</small></span></button>
        <i>→</i>
        <button type="button" @click="router.push('/integration-assets/services')"><b>5</b><span><strong>适配与路由</strong><small>绑定多个厂商并设置优先级、权重</small></span></button>
        <i>→</i>
        <button type="button" @click="router.push('/integration-assets/runtime-invoke')"><b>6</b><span><strong>测试发布</strong><small>验证后通过统一入口调用</small></span></button>
      </div>
    </div>

    <div class="product-concept-grid">
      <article class="surface product-concept-card"><span>提供方层</span><h3>第三方系统 → 通道 → 接口</h3><p>通道吸收地址、凭据和公共参数；接口只描述 Path、Method 和差异配置。</p><el-button link type="primary" @click="router.push('/integration-assets/channels')">管理接入配置 →</el-button></article>
      <article class="surface product-concept-card"><span>服务层</span><h3>serviceCode → 多个适配目标</h3><p>业务调用稳定服务，Mapping 和 Policy 负责适配不同厂商的字段、认证和报文。</p><el-button link type="primary" @click="router.push('/integration-assets/services')">管理接入服务 →</el-button></article>
      <article class="surface product-concept-card"><span>运行层</span><h3>规则过滤 → 优先级 → 权重</h3><p>在健康的候选目标中做可解释路由，并记录每次选择结果和失败切换证据。</p><el-button link type="primary" @click="router.push('/integration-assets/runtime-invoke')">进入调用控制台 →</el-button></article>
    </div>
  </section>
</template>
