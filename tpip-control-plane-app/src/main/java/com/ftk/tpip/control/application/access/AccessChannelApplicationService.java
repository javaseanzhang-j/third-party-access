package com.ftk.tpip.control.application.access;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.access.domain.model.AccessChannel;
import com.ftk.tpip.access.domain.model.AccessChannelStatus;
import com.ftk.tpip.access.domain.model.AccessParameter;
import com.ftk.tpip.access.domain.model.AccessParameterDataType;
import com.ftk.tpip.access.domain.model.AccessParameterLocation;
import com.ftk.tpip.access.domain.model.AccessParameterOverrideMode;
import com.ftk.tpip.access.domain.model.AccessParameterScope;
import com.ftk.tpip.access.domain.model.AccessParameterSource;
import com.ftk.tpip.access.domain.model.EffectiveAccessParameter;
import com.ftk.tpip.access.domain.repository.AccessChannelRepository;
import com.ftk.tpip.access.domain.service.AccessParameterResolver;
import com.ftk.tpip.provider.domain.model.CredentialStatus;
import com.ftk.tpip.provider.domain.model.ProviderStatus;
import com.ftk.tpip.provider.domain.repository.CredentialRefRepository;
import com.ftk.tpip.provider.domain.repository.ProviderContractRepository;
import com.ftk.tpip.provider.domain.repository.ProviderRepository;
import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessChannelApplicationService {
    private final AccessChannelRepository channels;
    private final ProviderRepository providers;
    private final CredentialRefRepository credentials;
    private final ProviderContractRepository contracts;
    private final ObjectMapper json;
    private final AccessParameterResolver resolver = new AccessParameterResolver();

    public AccessChannelApplicationService(AccessChannelRepository channels, ProviderRepository providers,
            CredentialRefRepository credentials, ProviderContractRepository contracts, ObjectMapper json) {
        this.channels = channels; this.providers = providers; this.credentials = credentials;
        this.contracts = contracts; this.json = json;
    }

    @Transactional
    public AccessChannel create(long providerId, String code, String name, String baseUrl,
            Long credentialRefId, String description, String actor) {
        requireProvider(providerId);
        requireCredential(providerId, credentialRefId);
        AssetCode normalizedCode = AssetCode.of(code);
        if (channels.findByCode(normalizedCode.value()).isPresent()) {
            throw new IllegalArgumentException("channelCode already exists");
        }
        return channels.create(AccessChannel.create(providerId, normalizedCode, name, baseUrl,
                credentialRefId, description), actor(actor));
    }

    @Transactional(readOnly = true)
    public AccessChannel get(long id) { return channel(id); }

    @Transactional(readOnly = true)
    public List<AccessChannel> findAll(Long providerId) { return channels.findAll(providerId); }

    @Transactional
    public AccessChannel update(long id, String name, String baseUrl, Long credentialRefId,
            String description, AccessChannelStatus status, long rowVersion, String actor) {
        AccessChannel current = channel(id);
        requireCredential(current.providerId(), credentialRefId);
        return channels.update(current.revise(name, baseUrl, credentialRefId, description, status, rowVersion), actor(actor));
    }

    @Transactional
    public void attachInterface(long channelId, long providerContractId, String actor) {
        AccessChannel channel = channel(channelId);
        var contract = contracts.findById(providerContractId)
                .orElseThrow(() -> new IllegalArgumentException("providerContractId does not exist"));
        if (contract.providerId() != channel.providerId()) {
            throw new IllegalArgumentException("interface and channel must belong to the same provider");
        }
        channels.attachInterface(channelId, providerContractId, actor(actor));
    }

    @Transactional(readOnly = true)
    public List<Long> interfaceIds(long channelId) { channel(channelId); return channels.findInterfaceIds(channelId); }

    @Transactional
    public AccessParameter upsertParameter(long channelId, AccessParameterScope scope, Long providerContractId,
            String code, String name, AccessParameterLocation location, AccessParameterSource source,
            AccessParameterDataType dataType, JsonNode value, String sourceSelector, Long secretRefId,
            AccessParameterOverrideMode overrideMode, boolean required, boolean sensitive,
            boolean callerOverridable, String description, String actor) {
        AccessChannel channel = channel(channelId);
        if (scope == AccessParameterScope.INTERFACE && (providerContractId == null
                || !channels.hasInterface(channelId, providerContractId))) {
            throw new IllegalArgumentException("interface must be attached to the channel before configuring overrides");
        }
        requireCredential(channel.providerId(), secretRefId);
        String valueDocument = canonical(value);
        AccessParameter parameter = new AccessParameter(null, channelId, scope, providerContractId, code, name,
                location, source, dataType, valueDocument, sourceSelector, secretRefId, overrideMode,
                required, sensitive || source == AccessParameterSource.SECRET_REF, callerOverridable,
                description, 0, null, null);
        return channels.upsertParameter(parameter, actor(actor));
    }

    @Transactional(readOnly = true)
    public List<AccessParameter> parameters(long channelId) { channel(channelId); return channels.findParameters(channelId); }

    @Transactional(readOnly = true)
    public List<EffectiveAccessParameter> effectiveParameters(long channelId, long providerContractId) {
        channel(channelId);
        if (!channels.hasInterface(channelId, providerContractId)) {
            throw new IllegalArgumentException("interface is not attached to the channel");
        }
        return resolver.resolve(channels.findParameters(channelId), providerContractId);
    }

    private AccessChannel channel(long id) {
        return channels.findById(id).orElseThrow(() -> new IllegalArgumentException("access channel does not exist"));
    }

    private void requireProvider(long id) {
        var provider = providers.findById(id).orElseThrow(() -> new IllegalArgumentException("providerId does not exist"));
        if (provider.status() != ProviderStatus.ACTIVE) throw new IllegalArgumentException("provider must be ACTIVE");
    }

    private void requireCredential(long providerId, Long id) {
        if (id == null) return;
        var credential = credentials.findById(id).orElseThrow(() -> new IllegalArgumentException("credentialRefId does not exist"));
        if (credential.providerId() != providerId || credential.status() != CredentialStatus.ACTIVE) {
            throw new IllegalArgumentException("credential must be ACTIVE and belong to the channel provider");
        }
    }

    private String canonical(JsonNode value) {
        if (value == null || value.isNull()) return null;
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("value is not valid JSON", exception); }
    }

    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new IllegalArgumentException("X-Operator must contain 1 to 100 characters");
        }
        return value.trim();
    }
}
