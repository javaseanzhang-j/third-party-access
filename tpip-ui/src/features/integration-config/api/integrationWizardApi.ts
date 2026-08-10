import { ApiError } from '@/api/http'
import { bindingVersionApi } from './bindingVersionApi'
import { canonicalAssetApi } from './canonicalAssetApi'
import { deploymentApi } from './deploymentApi'
import { fixtureSuiteApi } from './fixtureSuiteApi'
import { integrationAssetApi } from './integrationAssetApi'
import { mappingAssetApi } from './mappingAssetApi'
import { providerContractVersionApi } from './providerContractVersionApi'
import { workspaceReleaseApi } from './workspaceReleaseApi'
import { workspaceVerificationApi } from './workspaceVerificationApi'
import type { IntegrationWizardFacts } from '../model/integrationWizardModel'

export interface IntegrationWizardSelection {
  operationId: number
  operationCode: string
  providerContractId: number
  bindingId: number | null
  workspaceId: number | null
  environmentCode: string
}

async function activeRouteExists(operationCode: string, environmentCode: string, signal?: AbortSignal): Promise<boolean> {
  try {
    const route = await deploymentApi.activeRoute(operationCode, environmentCode, signal)
    return route.targets.length > 0
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return false
    throw error
  }
}

export const integrationWizardApi = {
  assess: async (selection: IntegrationWizardSelection, signal?: AbortSignal): Promise<IntegrationWizardFacts> => {
    const [canonicalContracts, providerVersions, endpoints] = await Promise.all([
      canonicalAssetApi.contracts(selection.operationId, signal),
      providerContractVersionApi.versions(selection.providerContractId, signal),
      integrationAssetApi.endpoints(selection.providerContractId, signal)
    ])
    const canonicalVersionSets = await Promise.all(canonicalContracts.items.map(async contract => ({
      kind: contract.contractKind,
      versions: await canonicalAssetApi.versions(contract.id, signal)
    })))
    const publishedCanonicalKinds = canonicalVersionSets
      .filter(item => item.versions.some(version => version.lifecycleStatus === 'PUBLISHED'))
      .map(item => item.kind)

    let publishedMappingDirections: string[] = []
    let publishedBindingVersionIds: number[] = []
    let hasPublishedFixtureVersion = false
    if (selection.bindingId) {
      const [mappings, bindingVersions, suites] = await Promise.all([
        mappingAssetApi.mappings(selection.bindingId, signal),
        bindingVersionApi.versions(selection.bindingId, signal),
        fixtureSuiteApi.suites(selection.bindingId, signal)
      ])
      const mappingVersionSets = await Promise.all(mappings.items.map(async mapping => ({
        direction: mapping.direction, versions: await mappingAssetApi.versions(mapping.id, signal)
      })))
      publishedMappingDirections = mappingVersionSets
        .filter(item => item.versions.some(version => version.lifecycleStatus === 'PUBLISHED'))
        .map(item => item.direction)
      publishedBindingVersionIds = bindingVersions
        .filter(version => version.lifecycleStatus === 'PUBLISHED').map(version => version.id)
      const fixtureVersionSets = await Promise.all(suites.map(suite => fixtureSuiteApi.versions(suite.id, signal)))
      hasPublishedFixtureVersion = fixtureVersionSets.some(versions =>
        versions.some(version => version.lifecycleStatus === 'PUBLISHED'))
    }

    let workspaceSelected = false
    let workspaceHasBindingVersion = false
    let hasPassedVerification = false
    let workspaceLifecycle: string | null = null
    let hasPublishedBundle = false
    if (selection.workspaceId) {
      const [workspaces, assets, jobs, bundles] = await Promise.all([
        workspaceVerificationApi.workspaces(signal),
        workspaceVerificationApi.assets(selection.workspaceId, signal),
        workspaceVerificationApi.jobs(selection.workspaceId, signal),
        workspaceReleaseApi.bundles(selection.workspaceId, signal)
      ])
      const workspace = workspaces.find(item => item.id === selection.workspaceId)
      workspaceSelected = Boolean(workspace)
      workspaceLifecycle = workspace?.lifecycleStatus ?? null
      workspaceHasBindingVersion = assets.some(asset => publishedBindingVersionIds.includes(asset.assetVersionId))
      hasPassedVerification = jobs.some(job => job.status === 'PASSED' && job.totalCount > 0 && job.failedCount === 0)
      hasPublishedBundle = bundles.some(bundle => bundle.lifecycleStatus === 'PUBLISHED')
    }

    const [deployments, hasActiveRoute] = await Promise.all([
      deploymentApi.deployments(selection.operationCode, selection.environmentCode, signal),
      activeRouteExists(selection.operationCode, selection.environmentCode, signal)
    ])
    return {
      hasOperation: true,
      publishedCanonicalKinds,
      hasProviderContract: true,
      hasPublishedProviderVersion: providerVersions.some(version => version.lifecycleStatus === 'PUBLISHED'),
      hasPublishedEndpoint: endpoints.items.some(endpoint => endpoint.environmentCode === selection.environmentCode &&
        endpoint.lifecycleStatus === 'PUBLISHED'),
      hasBinding: Boolean(selection.bindingId),
      publishedMappingDirections,
      hasPublishedBindingVersion: publishedBindingVersionIds.length > 0,
      hasPublishedFixtureVersion,
      workspaceSelected,
      workspaceHasBindingVersion,
      hasPassedVerification,
      workspaceLifecycle,
      hasPublishedBundle,
      hasDeployment: deployments.length > 0,
      hasActiveRoute
    }
  }
}
