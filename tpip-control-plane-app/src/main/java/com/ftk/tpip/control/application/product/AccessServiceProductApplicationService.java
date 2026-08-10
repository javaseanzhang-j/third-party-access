package com.ftk.tpip.control.application.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.catalog.domain.model.*;
import com.ftk.tpip.catalog.domain.repository.*;
import com.ftk.tpip.access.domain.repository.AccessChannelRepository;
import com.ftk.tpip.control.application.catalog.CanonicalSchemaContentCanonicalizer;
import com.ftk.tpip.control.application.integration.IntegrationBindingVersionApplicationService;
import com.ftk.tpip.control.application.integration.IntegrationMappingApplicationService;
import com.ftk.tpip.control.application.integration.IntegrationPolicyApplicationService;
import com.ftk.tpip.control.application.integration.MappingRuleInput;
import com.ftk.tpip.control.application.provider.ProviderEndpointApplicationService;
import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.integration.domain.repository.IntegrationBindingRepository;
import com.ftk.tpip.provider.domain.model.*;
import com.ftk.tpip.provider.domain.repository.*;
import com.ftk.tpip.shared.AssetCode;
import com.ftk.tpip.shared.SemanticVersion;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Product-facing aggregate over the technical catalog assets. */
@Service
public class AccessServiceProductApplicationService {
    private static final AssetCode PRODUCT_DOMAIN = AssetCode.of("tpip.integration");
    private static final AssetCode PRODUCT_CAPABILITY = AssetCode.of("tpip.access-services");

    private final CatalogHierarchyRepository catalog;
    private final CanonicalOperationRepository operations;
    private final CanonicalContractRepository contracts;
    private final IntegrationBindingRepository bindings;
    private final ProviderContractRepository providerContracts;
    private final ProviderRepository providers;
    private final CanonicalSchemaContentCanonicalizer canonicalizer;
    private final ProviderEndpointRepository endpoints;
    private final CredentialRefRepository credentials;
    private final IntegrationMappingApplicationService mappingService;
    private final IntegrationPolicyApplicationService policyService;
    private final IntegrationBindingVersionApplicationService bindingVersionService;
    private final AccessChannelRepository accessChannels;
    private final InterfaceTransportRepository interfaceTransports;
    private final ProviderEndpointApplicationService endpointService;

    public AccessServiceProductApplicationService(CatalogHierarchyRepository catalog,
            CanonicalOperationRepository operations, CanonicalContractRepository contracts,
            IntegrationBindingRepository bindings, ProviderContractRepository providerContracts,
            ProviderRepository providers, CanonicalSchemaContentCanonicalizer canonicalizer,
            ProviderEndpointRepository endpoints, CredentialRefRepository credentials,
            IntegrationMappingApplicationService mappingService,
            IntegrationPolicyApplicationService policyService,
            IntegrationBindingVersionApplicationService bindingVersionService,
            AccessChannelRepository accessChannels, InterfaceTransportRepository interfaceTransports,
            ProviderEndpointApplicationService endpointService) {
        this.catalog = catalog;
        this.operations = operations;
        this.contracts = contracts;
        this.bindings = bindings;
        this.providerContracts = providerContracts;
        this.providers = providers;
        this.canonicalizer = canonicalizer;
        this.endpoints = endpoints; this.credentials = credentials; this.mappingService = mappingService;
        this.policyService = policyService; this.bindingVersionService = bindingVersionService;
        this.accessChannels = accessChannels;
        this.interfaceTransports = interfaceTransports;
        this.endpointService = endpointService;
    }

    @Transactional
    public AccessServiceView create(CreateCommand command, String actor) {
        String user = actor(actor);
        AssetCode serviceCode = AssetCode.of(command.serviceCode());
        if (serviceCode.value().length() > 140) throw new IllegalArgumentException("serviceCode must not exceed 140 characters");
        if (operations.findByCode(serviceCode).isPresent()) throw new IllegalArgumentException("serviceCode already exists: " + serviceCode.value());
        long capabilityId = ensureProductCapability(user);
        CanonicalOperation operation = operations.create(CanonicalOperation.create(capabilityId, serviceCode,
                command.serviceName(), command.description(), command.invocationMode(), command.idempotencyClass(),
                command.dataClassification(), command.ownerCode()), user);
        createPublishedContract(operation, ContractKind.REQUEST, command.requestSchema(), command.requestExample(), user);
        createPublishedContract(operation, ContractKind.RESPONSE, command.responseSchema(), command.responseExample(), user);
        return view(operation);
    }

    @Transactional(readOnly = true)
    public List<AccessServiceView> list() {
        return operations.findAll(new CanonicalOperationQuery(null, null, null, 0, 200)).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public AccessServiceView get(long id) {
        return view(operations.findById(id).orElseThrow(() -> new IllegalArgumentException("Access service does not exist: " + id)));
    }

    @Transactional
    public AdapterTargetView addTarget(long serviceId, long providerContractId, String targetName, String ownerCode, String actor) {
        return target(createTarget(serviceId, providerContractId, targetName, ownerCode,
                ".target." + providerContractId, true, actor(actor)));
    }

    private IntegrationBinding createTarget(long serviceId, long providerContractId, String targetName,
            String ownerCode, String codeSuffix, boolean rejectSameInterface, String user) {
        CanonicalOperation service = operations.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Access service does not exist: " + serviceId));
        ProviderContract providerContract = providerContracts.findById(providerContractId)
                .orElseThrow(() -> new IllegalArgumentException("Third-party interface does not exist: " + providerContractId));
        if (service.status() != OperationStatus.ACTIVE) throw new IllegalArgumentException("Access service must be ACTIVE");
        if (providerContract.status() != ContractStatus.ACTIVE) throw new IllegalArgumentException("Third-party interface must be ACTIVE");
        if (rejectSameInterface && bindings.findAll(new IntegrationBindingQuery(serviceId, providerContractId,
                null, null, 0, 1)).stream().findAny().isPresent())
            throw new IllegalArgumentException("The third-party interface is already an adapter target of this service");
        AssetCode code = AssetCode.of(service.operationCode().value() + codeSuffix);
        if (bindings.findByCode(code).isPresent())
            throw new IllegalArgumentException("The third-party interface and channel are already an adapter target of this service");
        return bindings.create(IntegrationBinding.create(code, targetName, serviceId, providerContractId, ownerCode), user);
    }

    /** Materializes a complete executable adapter target from product-facing wizard input. */
    @Transactional
    public ProvisionedTargetView provisionTarget(long serviceId, ProvisionTargetCommand command, String actor) {
        String user = actor(actor);
        ProviderContract selectedContract = providerContracts.findById(command.providerContractId())
                .orElseThrow(() -> new IllegalArgumentException("The selected third-party interface does not exist"));
        var selectedChannel = accessChannels.findById(command.accessChannelId())
                .orElseThrow(() -> new IllegalArgumentException("The selected access channel does not exist"));
        if (selectedChannel.providerId() != selectedContract.providerId()
                || !accessChannels.hasInterface(selectedChannel.id(), selectedContract.id())) {
            throw new IllegalArgumentException("The access channel must belong to the same provider and contain the selected interface");
        }
        IntegrationBinding binding = createTarget(serviceId, command.providerContractId(), command.targetName(),
                command.ownerCode(), ".t." + command.providerContractId() + ".c." + command.accessChannelId(),
                false, user);
        AdapterTargetView created = target(binding);
        AccessServiceView service = get(serviceId);
        ContractView requestContract = requiredContract(service, ContractKind.REQUEST);
        ContractView responseContract = requiredContract(service, ContractKind.RESPONSE);
        ProviderContractVersion providerVersion = providerContracts
                .findVersionById(command.providerContractId(), command.providerContractVersionId())
                .orElseThrow(() -> new IllegalArgumentException("The selected third-party protocol version does not exist"));
        if (providerVersion.lifecycleStatus() != ContractLifecycleStatus.PUBLISHED)
            throw new IllegalArgumentException("The selected third-party protocol version must be PUBLISHED");
        ProviderEndpoint endpoint = endpoints.findById(command.endpointId())
                .orElseThrow(() -> new IllegalArgumentException("The selected endpoint does not exist"));
        if (endpoint.providerContractId() != command.providerContractId()
                || endpoint.lifecycleStatus() != EndpointLifecycleStatus.PUBLISHED)
            throw new IllegalArgumentException("The selected endpoint must be a published endpoint of the third-party interface");

        String canonicalRequestRef = schemaRef("canonical", requestContract.contractId(), requestContract.versionId());
        String canonicalResponseRef = schemaRef("canonical", responseContract.contractId(), responseContract.versionId());
        String providerRef = schemaRef("provider", command.providerContractId(), providerVersion.id());
        JsonNode emptyOptions = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        IntegrationMapping outbound = mappingService.create(binding.id(), binding.bindingCode().value() + ".request",
                command.targetName() + "请求字段映射", MappingAssetDirection.OUTBOUND_REQUEST, user);
        IntegrationMappingVersion outboundVersion = mappingService.createVersion(outbound.id(), SelectorProfile.JSONPATH_1_0,
                canonicalRequestRef, providerRef, emptyOptions, rules(command.requestMappings()), user);
        outboundVersion = mappingService.publish(outbound.id(), outboundVersion.id(), user);
        IntegrationMapping inbound = mappingService.create(binding.id(), binding.bindingCode().value() + ".response",
                command.targetName() + "返回字段映射", MappingAssetDirection.INBOUND_RESPONSE, user);
        IntegrationMappingVersion inboundVersion = mappingService.createVersion(inbound.id(), SelectorProfile.JSONPATH_1_0,
                providerRef, canonicalResponseRef, emptyOptions, rules(command.responseMappings()), user);
        inboundVersion = mappingService.publish(inbound.id(), inboundVersion.id(), user);

        Long policyVersionId = createPolicy(binding, command.authentication(), user);
        IntegrationBindingVersion version = bindingVersionService.create(binding.id(), requestContract.versionId(),
                responseContract.versionId(), providerVersion.id(), endpoint.id(), command.accessChannelId(),
                outboundVersion.id(), inboundVersion.id(), null, policyVersionId, null, emptyOptions, emptyOptions, user);
        version = bindingVersionService.publish(binding.id(), version.id(), user);
        return new ProvisionedTargetView(target(binding), version.id(), version.versionNo(), version.lifecycleStatus(),
                command.accessChannelId(), endpoint.id(), outboundVersion.id(), inboundVersion.id(), policyVersionId);
    }

    /**
     * Business-model entry point. The caller selects a channel and an interface transport version;
     * the legacy Endpoint execution snapshot is generated and published automatically.
     */
    @Transactional
    public ProvisionedTargetView provisionBusinessTarget(long serviceId,
            BusinessProvisionTargetCommand command, String actor) {
        String user = actor(actor);
        ProviderContract contract = providerContracts.findById(command.providerContractId())
                .orElseThrow(() -> new IllegalArgumentException("The selected third-party interface does not exist"));
        var channel = accessChannels.findById(command.accessChannelId())
                .orElseThrow(() -> new IllegalArgumentException("The selected access channel does not exist"));
        if (channel.providerId() != contract.providerId()
                || !accessChannels.hasInterface(channel.id(), contract.id()))
            throw new IllegalArgumentException("The access channel must contain the selected third-party interface");
        InterfaceTransportVersion transport = interfaceTransports
                .findVersionById(contract.id(), command.transportVersionId())
                .orElseThrow(() -> new IllegalArgumentException("The selected interface transport version does not exist"));
        if (transport.lifecycleStatus() != EndpointLifecycleStatus.PUBLISHED)
            throw new IllegalArgumentException("The selected interface transport version must be PUBLISHED");
        int connectTimeout = transport.connectTimeoutMs() == null ? 3_000 : transport.connectTimeoutMs();
        int readTimeout = transport.readTimeoutMs() == null ? 5_000 : transport.readTimeoutMs();
        int totalTimeout = transport.totalTimeoutMs() == null
                ? Math.max(8_000, Math.max(connectTimeout, readTimeout)) : transport.totalTimeoutMs();
        EndpointScheme scheme = EndpointScheme.fromValue(java.net.URI.create(channel.baseUrl()).getScheme());
        ProviderEndpoint endpoint = endpointService.createRevision(contract.id(),
                "generated.c" + channel.id() + ".i" + contract.id(), "local", scheme,
                channel.baseUrl(), transport.resourcePath(), transport.httpMethod(), transport.contentType(),
                transport.charsetName(), connectTimeout, readTimeout, totalTimeout, null, null, null, user);
        endpoint = endpointService.publish(endpoint.id(), user);
        return provisionTarget(serviceId, new ProvisionTargetCommand(command.providerContractId(),
                command.providerContractVersionId(), command.accessChannelId(), endpoint.id(),
                command.targetName(), command.ownerCode(), command.requestMappings(),
                command.responseMappings(), null), user);
    }

    private Long createPolicy(IntegrationBinding binding, AuthenticationTemplate authentication, String actor) {
        if (authentication == null || authentication.mode() == AuthenticationMode.CHANNEL_PARAMETERS) return null;
        CredentialRef credential = credentials.findById(Objects.requireNonNull(authentication.credentialRefId(),
                "credentialRefId is required for policy authentication"))
                .orElseThrow(() -> new IllegalArgumentException("The selected credential does not exist"));
        ProviderContract contract = providerContracts.findById(binding.providerContractId()).orElseThrow();
        if (credential.providerId() != contract.providerId() || credential.status() != CredentialStatus.ACTIVE)
            throw new IllegalArgumentException("The selected credential must be ACTIVE and belong to the provider");
        var factory = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
        var root = factory.objectNode().put("apiVersion", "tpip.policy/v1alpha1").put("kind", "PolicyChain");
        var with = factory.objectNode().put("secretRef", credential.secretUri());
        String stepId;
        String policyType;
        if (authentication.mode() == AuthenticationMode.API_KEY_POLICY) {
            stepId = "authentication";
            policyType = "builtin.auth.api-key@1.0.0";
            with.put("headerName", optional(authentication.headerName(), "X-API-Key"))
                    .put("prefix", optional(authentication.prefix(), ""));
        } else if (authentication.mode() == AuthenticationMode.HMAC_SHA256_POLICY) {
            stepId = "authentication";
            policyType = "builtin.auth.hmac-sha256@1.0.0";
            with.put("sourceTemplate", required(authentication.sourceTemplate(), "sourceTemplate"))
                    .put("headerName", optional(authentication.headerName(), "X-Signature"))
                    .put("encoding", optional(authentication.encoding(), "HEX_LOWER"))
                    .put("prefix", optional(authentication.prefix(), ""));
        } else {
            throw new IllegalArgumentException("Unsupported authentication template: " + authentication.mode());
        }
        var step = factory.objectNode().put("id", stepId).put("use", policyType).put("onFailure", "FAIL");
        step.set("with", with);
        root.set("stages", factory.objectNode().set("BEFORE_TRANSPORT", factory.arrayNode().add(step)));
        IntegrationPolicy policy = policyService.create(binding.id(), binding.bindingCode().value() + ".policy",
                binding.bindingName() + "认证规则", actor);
        IntegrationPolicyVersion policyVersion = policyService.createVersion(policy.id(), root, actor);
        return policyService.publish(policy.id(), policyVersion.id(), actor).id();
    }

    private static List<MappingRuleInput> rules(List<FieldMappingCommand> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("Request and response field mappings must not be empty");
        int[] order = {0};
        return values.stream().map(value -> new MappingRuleInput("field." + (++order[0]), order[0] * 10,
                MappingValueSource.SELECTOR, value.sourcePath(), value.targetPath(),
                value.targetType() == null ? MappingTargetType.STRING : value.targetType(), null, null, null, null,
                null, value.required(), null, value.required() ? MappingMissingStrategy.FAIL : MappingMissingStrategy.IGNORE,
                MappingErrorStrategy.FAIL, true)).toList();
    }

    private static ContractView requiredContract(AccessServiceView service, ContractKind kind) {
        ContractView value = service.contracts().stream().filter(item -> item.kind() == kind).findFirst()
                .orElseThrow(() -> new IllegalStateException("Access service has no published " + kind + " contract"));
        if (value.versionId() == null || value.lifecycleStatus() != CanonicalContractLifecycleStatus.PUBLISHED)
            throw new IllegalStateException("Access service " + kind + " contract must be PUBLISHED");
        return value;
    }
    private static String schemaRef(String kind, long contractId, long versionId) {
        return kind + "-contract-version:" + contractId + ":" + versionId;
    }
    private static String optional(String value, String fallback) { return value == null ? fallback : value.trim(); }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private long ensureProductCapability(String actor) {
        BusinessDomain domain = catalog.findDomainByCode(PRODUCT_DOMAIN).orElseGet(() ->
                catalog.createDomain(BusinessDomain.create(PRODUCT_DOMAIN, "第三方接入服务",
                        "接入服务产品模型的内部技术目录", "tpip-platform"), actor));
        if (domain.status() != CatalogAssetStatus.ACTIVE) throw new IllegalStateException("The internal access-service domain is INACTIVE");
        CanonicalCapability capability = catalog.findCapabilityByCode(PRODUCT_CAPABILITY).orElseGet(() ->
                catalog.createCapability(CanonicalCapability.create(domain.id(), PRODUCT_CAPABILITY, "统一接入服务",
                        "由产品化接入服务页面自动维护", "tpip-platform"), actor));
        if (capability.status() != CatalogAssetStatus.ACTIVE) throw new IllegalStateException("The internal access-service capability is INACTIVE");
        return capability.id();
    }

    private void createPublishedContract(CanonicalOperation operation, ContractKind kind, JsonNode schema,
            JsonNode example, String actor) {
        String suffix = kind == ContractKind.REQUEST ? "request" : "response";
        String display = kind == ContractKind.REQUEST ? "标准请求" : "标准返回";
        CanonicalContract contract = contracts.create(CanonicalContract.create(operation.id(),
                AssetCode.of(operation.operationCode().value() + "." + suffix), operation.operationName() + display,
                kind, "接入服务的" + display + "契约"), actor);
        var content = canonicalizer.canonicalize(SchemaStandard.JSON_SCHEMA_2020_12, schema, example, CompatibilityMode.BACKWARD);
        CanonicalContractVersion version = contracts.createVersion(CanonicalContractVersion.draft(contract.id(),
                SemanticVersion.parse("1.0.0"), SchemaStandard.JSON_SCHEMA_2020_12, content.schemaDocument(),
                content.exampleDocument(), CompatibilityMode.BACKWARD, content.checksum()), actor);
        contracts.publishVersion(contract.id(), version.id(), actor);
    }

    private AccessServiceView view(CanonicalOperation operation) {
        Map<ContractKind, ContractView> contractByKind = new LinkedHashMap<>();
        contracts.findAll(new CanonicalContractQuery(operation.id(), null, null, null, 0, 200)).stream()
                .filter(item -> item.contractKind() == ContractKind.REQUEST || item.contractKind() == ContractKind.RESPONSE)
                .forEach(item -> contractByKind.putIfAbsent(item.contractKind(), contract(item)));
        List<ContractView> standardContracts = List.copyOf(contractByKind.values());
        List<AdapterTargetView> targets = bindings.findAll(new IntegrationBindingQuery(operation.id(), null, null, null, 0, 200))
                .stream().map(this::target).toList();
        return new AccessServiceView(operation.id(), operation.operationCode().value(), operation.operationName(),
                operation.description(), operation.invocationMode(), operation.idempotencyClass(), operation.dataClassification(),
                operation.ownerCode(), operation.status(), operation.rowVersion(), standardContracts, targets);
    }

    private ContractView contract(CanonicalContract contract) {
        List<CanonicalContractVersion> versions = contracts.findVersions(contract.id());
        CanonicalContractVersion latest = versions.isEmpty() ? null : versions.getFirst();
        return new ContractView(contract.id(), contract.contractKind(), contract.contractName(), latest == null ? null : latest.id(),
                latest == null ? null : latest.semanticVersion().toString(), latest == null ? null : latest.lifecycleStatus());
    }

    private AdapterTargetView target(IntegrationBinding binding) {
        ProviderContract contract = providerContracts.findById(binding.providerContractId())
                .orElseThrow(() -> new IllegalStateException("Binding references missing provider contract: " + binding.providerContractId()));
        Provider provider = providers.findById(contract.providerId())
                .orElseThrow(() -> new IllegalStateException("Provider contract references missing provider: " + contract.providerId()));
        return new AdapterTargetView(binding.id(), binding.bindingCode().value(), binding.bindingName(), binding.status(),
                contract.id(), contract.contractCode().value(), contract.contractName(), provider.id(), provider.providerName());
    }

    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) throw new IllegalArgumentException("X-Operator is blank or too long");
        return value.trim();
    }

    public record CreateCommand(String serviceCode, String serviceName, String description, InvocationMode invocationMode,
            IdempotencyClass idempotencyClass, DataClassification dataClassification, String ownerCode,
            JsonNode requestSchema, JsonNode requestExample, JsonNode responseSchema, JsonNode responseExample) {}
    public record AccessServiceView(long id, String serviceCode, String serviceName, String description,
            InvocationMode invocationMode, IdempotencyClass idempotencyClass, DataClassification dataClassification,
            String ownerCode, OperationStatus status, long rowVersion, List<ContractView> contracts,
            List<AdapterTargetView> targets) {}
    public record ContractView(long contractId, ContractKind kind, String contractName, Long versionId,
            String semanticVersion, CanonicalContractLifecycleStatus lifecycleStatus) {}
    public record AdapterTargetView(long bindingId, String targetCode, String targetName, BindingStatus status,
            long providerContractId, String interfaceCode, String interfaceName, long providerId, String providerName) {}
    public enum AuthenticationMode { CHANNEL_PARAMETERS, API_KEY_POLICY, HMAC_SHA256_POLICY }
    public record AuthenticationTemplate(AuthenticationMode mode, Long credentialRefId, String headerName, String prefix,
            String sourceTemplate, String encoding) {}
    public record FieldMappingCommand(String sourcePath, String targetPath, MappingTargetType targetType, boolean required) {}
    public record ProvisionTargetCommand(long providerContractId, long providerContractVersionId, long accessChannelId,
            long endpointId, String targetName, String ownerCode, List<FieldMappingCommand> requestMappings,
            List<FieldMappingCommand> responseMappings, AuthenticationTemplate authentication) {}
    public record BusinessProvisionTargetCommand(long providerContractId, long providerContractVersionId,
            long accessChannelId, long transportVersionId, String targetName, String ownerCode,
            List<FieldMappingCommand> requestMappings, List<FieldMappingCommand> responseMappings) {}
    public record ProvisionedTargetView(AdapterTargetView target, long bindingVersionId, int bindingVersionNo,
            BindingVersionLifecycleStatus lifecycleStatus, long accessChannelId, long endpointId,
            long requestMappingVersionId, long responseMappingVersionId, Long policyVersionId) {}
}
