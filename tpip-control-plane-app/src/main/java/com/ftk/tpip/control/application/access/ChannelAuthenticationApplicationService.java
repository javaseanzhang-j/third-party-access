package com.ftk.tpip.control.application.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.access.domain.model.*;
import com.ftk.tpip.access.domain.repository.*;
import com.ftk.tpip.control.application.integration.IntegrationPolicyApplicationService;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.policy.compiler.DefaultPolicyCompiler;
import com.ftk.tpip.provider.domain.repository.CredentialRefRepository;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChannelAuthenticationApplicationService {
    private final AccessChannelRepository channels;
    private final CredentialProfileRepository profiles;
    private final CredentialRefRepository secrets;
    private final AuthenticationTemplateRepository templates;
    private final ChannelAuthenticationRepository authentications;
    private final IntegrationPolicyApplicationService policyService;
    private final ChannelAuthenticationPolicyCompiler templateCompiler;
    private final CanonicalJsonService canonical;
    private final ObjectMapper json;
    public ChannelAuthenticationApplicationService(AccessChannelRepository channels,
            CredentialProfileRepository profiles, CredentialRefRepository secrets,
            AuthenticationTemplateRepository templates, ChannelAuthenticationRepository authentications,
            IntegrationPolicyApplicationService policyService, ChannelAuthenticationPolicyCompiler templateCompiler,
            CanonicalJsonService canonical, ObjectMapper json) {
        this.channels = channels; this.profiles = profiles; this.secrets = secrets; this.templates = templates;
        this.authentications = authentications; this.policyService = policyService;
        this.templateCompiler = templateCompiler; this.canonical = canonical; this.json = json;
    }

    @Transactional(readOnly = true)
    public List<View> versions(long channelId) { requireChannel(channelId); return authentications.findVersions(channelId).stream().map(this::view).toList(); }

    @Transactional
    public View create(long channelId, long templateVersionId, long credentialProfileId,
            JsonNode configuration, String actor) {
        AccessChannel channel = requireChannel(channelId);
        CredentialProfile profile = profiles.findById(credentialProfileId)
                .orElseThrow(() -> new IllegalArgumentException("credential profile does not exist"));
        if (profile.providerId() != channel.providerId() || profile.status() != AccessChannelStatus.ACTIVE)
            throw new IllegalArgumentException("credential profile must be ACTIVE and belong to the channel provider");
        AuthenticationTemplateVersion templateVersion = findTemplateVersion(templateVersionId);
        AuthenticationTemplate template = templates.findById(templateVersion.authenticationTemplateId()).orElseThrow();
        if (template.status() != AccessChannelStatus.ACTIVE || (template.providerId() != null && template.providerId() != channel.providerId()))
            throw new IllegalArgumentException("authentication template is not available for the channel provider");
        if (templateVersion.lifecycleStatus() != AccessPolicyLifecycleStatus.PUBLISHED)
            throw new IllegalArgumentException("authentication template version must be PUBLISHED");
        JsonNode normalizedConfiguration = configuration == null || configuration.isNull()
                ? json.createObjectNode() : object(configuration, "configuration");
        String configurationText = canonical.canonicalString(normalizedConfiguration);
        List<CredentialProfileItem> profileItems = profiles.findItems(profile.id());
        Map<Long, com.ftk.tpip.provider.domain.model.CredentialRef> secretRefs = profileItems.stream()
                .filter(item -> item.secretRefId() != null)
                .collect(Collectors.toMap(CredentialProfileItem::secretRefId,
                        item -> secrets.findById(item.secretRefId()).orElseThrow(
                                () -> new IllegalArgumentException("Secret reference does not exist"))));
        JsonNode policy = templateCompiler.compile(template, templateVersion, profile, profileItems,
                secretRefs, normalizedConfiguration);
        policyService.compileScoped("channel.authentication", 1, policy);
        String policyText = canonical.canonicalString(policy);
        var material = json.createObjectNode().put("channelId", channelId)
                .put("templateVersionId", templateVersionId).put("credentialProfileId", credentialProfileId);
        material.set("configuration", normalizedConfiguration); material.set("policy", policy);
        String checksum = canonical.sha256(canonical.canonicalString(material));
        ChannelAuthenticationVersion created = authentications.createVersion(new ChannelAuthenticationVersion(null,
                channelId, 0, templateVersionId, credentialProfileId, configurationText, policyText,
                DefaultPolicyCompiler.COMPILER_VERSION, checksum, AccessPolicyLifecycleStatus.DRAFT, null, null), actor(actor));
        return view(created);
    }

    @Transactional
    public View publish(long channelId, long versionId, String actor) {
        requireChannel(channelId);
        return view(authentications.publishVersion(channelId, versionId, actor(actor)));
    }

    private AuthenticationTemplateVersion findTemplateVersion(long versionId) {
        return templates.findVersionById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("authentication template version does not exist"));
    }
    private AccessChannel requireChannel(long id) {
        AccessChannel channel = channels.findById(id).orElseThrow(() -> new IllegalArgumentException("access channel does not exist"));
        if (channel.status() != AccessChannelStatus.ACTIVE) throw new IllegalArgumentException("access channel must be ACTIVE");
        return channel;
    }
    private View view(ChannelAuthenticationVersion value) {
        return new View(value.id(), value.channelId(), value.versionNo(), value.authenticationTemplateVersionId(),
                value.credentialProfileId(), read(value.configurationDocument()), value.contentChecksum(),
                value.lifecycleStatus().name(), value.publishedAt(), value.createdAt());
    }
    private JsonNode object(JsonNode value, String field) {
        if (!value.isObject()) throw new IllegalArgumentException(field + " must be a JSON object");
        return value;
    }
    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (Exception failure) { throw new IllegalStateException("Stored authentication JSON is invalid", failure); }
    }
    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) throw new IllegalArgumentException("X-Operator is invalid");
        return value.trim();
    }
    public record View(long id, long channelId, int versionNo, long authenticationTemplateVersionId,
            long credentialProfileId, JsonNode configuration, String contentChecksum, String lifecycleStatus,
            java.time.Instant publishedAt, java.time.Instant createdAt) {}
}
