package com.ftk.tpip.control.application.product;

import com.ftk.tpip.access.domain.model.AccessChannelStatus;
import com.ftk.tpip.access.domain.model.AccessPolicyLifecycleStatus;
import com.ftk.tpip.access.domain.repository.AccessChannelRepository;
import com.ftk.tpip.access.domain.repository.ChannelAuthenticationRepository;
import com.ftk.tpip.catalog.domain.model.CanonicalContractLifecycleStatus;
import com.ftk.tpip.catalog.domain.model.ContractKind;
import com.ftk.tpip.catalog.domain.model.OperationStatus;
import com.ftk.tpip.control.application.product.AccessServiceProductApplicationService.AccessServiceView;
import com.ftk.tpip.control.application.product.AccessServiceProductApplicationService.AdapterTargetView;
import com.ftk.tpip.integration.domain.model.BindingStatus;
import com.ftk.tpip.integration.domain.model.BindingVersionLifecycleStatus;
import com.ftk.tpip.integration.domain.model.IntegrationBindingVersion;
import com.ftk.tpip.integration.domain.model.MappingLifecycleStatus;
import com.ftk.tpip.integration.domain.repository.IntegrationBindingVersionRepository;
import com.ftk.tpip.integration.domain.repository.IntegrationMappingRepository;
import com.ftk.tpip.provider.domain.model.ContractLifecycleStatus;
import com.ftk.tpip.provider.domain.model.EndpointLifecycleStatus;
import com.ftk.tpip.provider.domain.repository.ProviderContractRepository;
import com.ftk.tpip.provider.domain.repository.ProviderEndpointRepository;
import com.ftk.tpip.routing.domain.model.RouteLifecycleStatus;
import com.ftk.tpip.routing.domain.repository.ServiceRouteRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Product-facing preflight that explains whether an access service can enter verification. */
@Service
public class AccessServiceReadinessApplicationService {
    private static final String SERVICE_PATH = "/integration-assets/services";
    private static final String PROVIDER_PATH = "/integration-assets/provider-access";
    private static final String VERIFY_PATH = "/integration-assets/workspaces";

    private final AccessServiceProductApplicationService products;
    private final IntegrationBindingVersionRepository bindingVersions;
    private final IntegrationMappingRepository mappings;
    private final ProviderContractRepository providerContracts;
    private final ProviderEndpointRepository endpoints;
    private final AccessChannelRepository channels;
    private final ChannelAuthenticationRepository authentications;
    private final ServiceRouteRepository routes;

    public AccessServiceReadinessApplicationService(AccessServiceProductApplicationService products,
            IntegrationBindingVersionRepository bindingVersions, IntegrationMappingRepository mappings,
            ProviderContractRepository providerContracts, ProviderEndpointRepository endpoints,
            AccessChannelRepository channels, ChannelAuthenticationRepository authentications,
            ServiceRouteRepository routes) {
        this.products = products;
        this.bindingVersions = bindingVersions;
        this.mappings = mappings;
        this.providerContracts = providerContracts;
        this.endpoints = endpoints;
        this.channels = channels;
        this.authentications = authentications;
        this.routes = routes;
    }

    @Transactional(readOnly = true)
    public ReadinessView readiness(long serviceId) {
        AccessServiceView service = products.get(serviceId);
        List<ReadinessCheck> checks = new ArrayList<>();
        checks.add(check("SERVICE_ACTIVE", "接入服务已启用",
                service.status() == OperationStatus.ACTIVE, "服务可被后续验证和发布流程使用",
                "接入服务尚未启用", SERVICE_PATH));
        checks.add(contractCheck(service, ContractKind.REQUEST, "标准请求报文"));
        checks.add(contractCheck(service, ContractKind.RESPONSE, "标准返回报文"));

        List<AdapterTargetView> activeTargets = service.targets().stream()
                .filter(target -> target.status() == BindingStatus.ACTIVE).toList();
        checks.add(check("ACTIVE_TARGET", "至少一个可用的第三方实现", !activeTargets.isEmpty(),
                "已配置 " + activeTargets.size() + " 个启用实现", "尚未配置可执行的第三方实现", SERVICE_PATH));

        List<TargetReadiness> targetResults = activeTargets.stream().map(this::targetReadiness).toList();
        checks.add(routeCheck(service.id(), activeTargets));
        ReadinessStatus status = summarize(checks, targetResults);
        String summary = switch (status) {
            case READY -> "配置完整，可以进入验证与发布";
            case READY_WITH_WARNINGS -> "核心配置完整，可以进入验证；请同时关注提示项";
            case BLOCKED -> "配置尚未完整，请先处理阻断项";
        };
        return new ReadinessView(service.id(), service.serviceCode(), status, summary, VERIFY_PATH,
                List.copyOf(checks), targetResults);
    }

    private ReadinessCheck contractCheck(AccessServiceView service, ContractKind kind, String name) {
        boolean published = service.contracts().stream().anyMatch(contract -> contract.kind() == kind
                && contract.versionId() != null
                && contract.lifecycleStatus() == CanonicalContractLifecycleStatus.PUBLISHED);
        return check("CANONICAL_" + kind, name + "已发布", published,
                name + "已有稳定的已发布版本", name + "缺少已发布版本", SERVICE_PATH);
    }

    private TargetReadiness targetReadiness(AdapterTargetView target) {
        List<ReadinessCheck> checks = new ArrayList<>();
        IntegrationBindingVersion version = bindingVersions.findVersions(target.bindingId()).stream()
                .filter(item -> item.lifecycleStatus() == BindingVersionLifecycleStatus.PUBLISHED)
                .findFirst().orElse(null);
        checks.add(check("EXECUTABLE_VERSION", "可执行配置已发布", version != null,
                version == null ? "" : "当前执行版本 v" + version.versionNo(),
                "尚未生成并发布可执行配置", SERVICE_PATH));
        if (version == null) {
            return targetResult(target, checks);
        }

        boolean protocolPublished = providerContracts
                .findVersionById(target.providerContractId(), version.providerContractVersionId())
                .filter(item -> item.lifecycleStatus() == ContractLifecycleStatus.PUBLISHED).isPresent();
        checks.add(check("PROVIDER_MESSAGE", "第三方报文结构已发布", protocolPublished,
                "执行版本已锁定第三方报文结构", "执行版本引用的第三方报文结构不可用", PROVIDER_PATH));

        boolean endpointPublished = endpoints.findById(version.endpointId())
                .filter(item -> item.providerContractId() == target.providerContractId())
                .filter(item -> item.lifecycleStatus() == EndpointLifecycleStatus.PUBLISHED).isPresent();
        checks.add(check("TRANSPORT", "调用地址与请求方式已发布", endpointPublished,
                "执行地址、Method 与 Path 已冻结", "调用地址或请求方式不可用", PROVIDER_PATH));

        boolean channelReady = version.accessChannelId() != null && channels.findById(version.accessChannelId())
                .filter(item -> item.status() == AccessChannelStatus.ACTIVE)
                .filter(item -> channels.hasInterface(item.id(), target.providerContractId())).isPresent();
        checks.add(check("CHANNEL", "接入通道可用", channelReady,
                "通道已启用并关联当前接口", "执行版本缺少可用的接入通道", PROVIDER_PATH));

        boolean authenticationPublished = version.accessChannelId() != null
                && authentications.findVersions(version.accessChannelId()).stream()
                .anyMatch(item -> item.lifecycleStatus() == AccessPolicyLifecycleStatus.PUBLISHED);
        checks.add(authenticationPublished
                ? pass("AUTHENTICATION", "通道认证已发布", "账号凭据和签名规则已形成发布快照", PROVIDER_PATH)
                : warn("AUTHENTICATION", "未配置通道认证", "仅当第三方接口允许匿名访问时可以忽略", PROVIDER_PATH));

        checks.add(mappingCheck("REQUEST_MAPPING", "请求字段转换已发布", version.requestMappingVersionId()));
        checks.add(mappingCheck("RESPONSE_MAPPING", "返回字段转换已发布", version.responseMappingVersionId()));
        return targetResult(target, checks);
    }

    private ReadinessCheck mappingCheck(String code, String name, Long versionId) {
        boolean published = versionId != null && mappings.findVersionById(versionId)
                .filter(item -> item.lifecycleStatus() == MappingLifecycleStatus.PUBLISHED).isPresent();
        return check(code, name, published, "字段映射已冻结到执行版本",
                name.replace("已发布", "缺失或未发布"), SERVICE_PATH);
    }

    private ReadinessCheck routeCheck(long serviceId, List<AdapterTargetView> targets) {
        if (targets.size() <= 1) {
            return pass("ROUTING", "调用选择规则", targets.isEmpty()
                    ? "添加实现后再确定调用选择规则" : "只有一个实现，平台固定使用该实现", SERVICE_PATH);
        }
        Set<Long> expected = targets.stream().map(AdapterTargetView::bindingId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        boolean published = routes.findPolicyByOperationId(serviceId).filter(policy -> policy.active())
                .flatMap(policy -> routes.findVersions(policy.id()).stream()
                        .filter(version -> version.lifecycleStatus() == RouteLifecycleStatus.PUBLISHED)
                        .findFirst())
                .map(version -> version.targets().stream().filter(item -> item.enabled())
                        .map(item -> item.bindingId()).collect(java.util.stream.Collectors.toSet())
                        .containsAll(expected))
                .orElse(false);
        return check("ROUTING", "多实现调用选择规则已发布", published,
                "已发布的路由覆盖全部启用实现", "多个实现需要发布优先级、权重或条件路由", SERVICE_PATH);
    }

    private static TargetReadiness targetResult(AdapterTargetView target, List<ReadinessCheck> checks) {
        ReadinessStatus status = summarize(checks, List.of());
        return new TargetReadiness(target.bindingId(), target.targetName(), target.providerName(),
                target.interfaceName(), status, List.copyOf(checks));
    }

    static ReadinessStatus summarize(List<ReadinessCheck> checks, List<TargetReadiness> targets) {
        boolean blocked = checks.stream().anyMatch(item -> item.status() == CheckStatus.BLOCK)
                || targets.stream().anyMatch(item -> item.status() == ReadinessStatus.BLOCKED);
        if (blocked) return ReadinessStatus.BLOCKED;
        boolean warning = checks.stream().anyMatch(item -> item.status() == CheckStatus.WARN)
                || targets.stream().anyMatch(item -> item.status() == ReadinessStatus.READY_WITH_WARNINGS);
        return warning ? ReadinessStatus.READY_WITH_WARNINGS : ReadinessStatus.READY;
    }

    private static ReadinessCheck check(String code, String name, boolean passed, String passedDetail,
            String blockedDetail, String actionPath) {
        return passed ? pass(code, name, passedDetail, actionPath)
                : new ReadinessCheck(code, name, CheckStatus.BLOCK, blockedDetail, actionPath);
    }
    private static ReadinessCheck pass(String code, String name, String detail, String actionPath) {
        return new ReadinessCheck(code, name, CheckStatus.PASS, detail, actionPath);
    }
    private static ReadinessCheck warn(String code, String name, String detail, String actionPath) {
        return new ReadinessCheck(code, name, CheckStatus.WARN, detail, actionPath);
    }

    public enum ReadinessStatus { READY, READY_WITH_WARNINGS, BLOCKED }
    public enum CheckStatus { PASS, WARN, BLOCK }
    public record ReadinessCheck(String code, String name, CheckStatus status, String detail, String actionPath) {}
    public record TargetReadiness(long bindingId, String targetName, String providerName, String interfaceName,
            ReadinessStatus status, List<ReadinessCheck> checks) {}
    public record ReadinessView(long serviceId, String serviceCode, ReadinessStatus status, String summary,
            String verificationPath, List<ReadinessCheck> checks, List<TargetReadiness> targets) {}
}
