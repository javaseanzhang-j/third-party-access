package com.ftk.tpip.control.application.access;

import com.ftk.tpip.access.domain.model.*;
import com.ftk.tpip.access.domain.repository.*;
import com.ftk.tpip.provider.domain.model.*;
import com.ftk.tpip.provider.domain.repository.*;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessRequestPreviewApplicationService {
    private final AccessChannelRepository channels;
    private final ProviderContractRepository contracts;
    private final InterfaceTransportRepository transports;
    private final ChannelAuthenticationRepository authentications;
    private final AuthenticationTemplateRepository templates;
    private final CredentialProfileRepository profiles;
    private final CredentialRefRepository secrets;
    private final BusinessRequestPreviewAssembler assembler;

    public BusinessRequestPreviewApplicationService(AccessChannelRepository channels,
            ProviderContractRepository contracts, InterfaceTransportRepository transports,
            ChannelAuthenticationRepository authentications, AuthenticationTemplateRepository templates,
            CredentialProfileRepository profiles, CredentialRefRepository secrets,
            BusinessRequestPreviewAssembler assembler) {
        this.channels = channels; this.contracts = contracts; this.transports = transports;
        this.authentications = authentications; this.templates = templates; this.profiles = profiles;
        this.secrets = secrets; this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public BusinessRequestPreviewAssembler.Preview preview(long channelId, long interfaceId,
            Long transportVersionId, Long authenticationVersionId) {
        AccessChannel channel = channels.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("access channel does not exist"));
        if (channel.status() != AccessChannelStatus.ACTIVE)
            throw new IllegalArgumentException("access channel must be ACTIVE");
        ProviderContract contract = contracts.findById(interfaceId)
                .orElseThrow(() -> new IllegalArgumentException("third-party interface does not exist"));
        if (contract.status() != ContractStatus.ACTIVE || contract.providerId() != channel.providerId()
                || !channels.hasInterface(channelId, interfaceId))
            throw new IllegalArgumentException("third-party interface is not available on the selected channel");
        InterfaceTransportVersion transport = transportVersionId == null
                ? latestPublishedTransport(interfaceId)
                : transports.findVersionById(interfaceId, transportVersionId)
                    .orElseThrow(() -> new IllegalArgumentException("interface transport version does not exist"));
        requirePublished(transport.lifecycleStatus(), "interface transport version");
        ChannelAuthenticationVersion authentication = authenticationVersionId == null
                ? latestPublishedAuthentication(channelId)
                : authentications.findVersionById(channelId, authenticationVersionId)
                    .orElseThrow(() -> new IllegalArgumentException("channel authentication version does not exist"));
        if (authentication.lifecycleStatus() != AccessPolicyLifecycleStatus.PUBLISHED)
            throw new IllegalArgumentException("channel authentication version must be PUBLISHED");
        AuthenticationTemplateVersion templateVersion = templates
                .findVersionById(authentication.authenticationTemplateVersionId())
                .orElseThrow(() -> new IllegalStateException("authentication template version is missing"));
        AuthenticationTemplate template = templates.findById(templateVersion.authenticationTemplateId())
                .orElseThrow(() -> new IllegalStateException("authentication template is missing"));
        CredentialProfile profile = profiles.findById(authentication.credentialProfileId())
                .orElseThrow(() -> new IllegalStateException("credential profile is missing"));
        var items = profiles.findItems(profile.id());
        Map<Long, CredentialRef> refs = items.stream().filter(item -> item.secretRefId() != null)
                .collect(Collectors.toMap(CredentialProfileItem::secretRefId,
                        item -> secrets.findById(item.secretRefId()).orElseThrow(
                                () -> new IllegalStateException("Secret reference is missing"))));
        return assembler.assemble(channel, contract, transport, authentication, template,
                templateVersion, profile, items, refs);
    }

    private InterfaceTransportVersion latestPublishedTransport(long interfaceId) {
        return transports.findVersions(interfaceId).stream()
                .filter(value -> value.lifecycleStatus() == EndpointLifecycleStatus.PUBLISHED)
                .max(Comparator.comparingInt(InterfaceTransportVersion::versionNo))
                .orElseThrow(() -> new IllegalArgumentException("interface has no published transport version"));
    }
    private ChannelAuthenticationVersion latestPublishedAuthentication(long channelId) {
        return authentications.findVersions(channelId).stream()
                .filter(value -> value.lifecycleStatus() == AccessPolicyLifecycleStatus.PUBLISHED)
                .max(Comparator.comparingInt(ChannelAuthenticationVersion::versionNo))
                .orElseThrow(() -> new IllegalArgumentException("channel has no published authentication version"));
    }
    private static void requirePublished(EndpointLifecycleStatus status, String label) {
        if (status != EndpointLifecycleStatus.PUBLISHED)
            throw new IllegalArgumentException(label + " must be PUBLISHED");
    }
}
