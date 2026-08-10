package com.ftk.tpip.control.application.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.NotificationRoutingAssetRepository;
import com.ftk.tpip.release.domain.repository.NotificationTemplateRepository;
import com.ftk.tpip.provider.domain.model.EndpointHttpMethod;
import com.ftk.tpip.provider.domain.model.EndpointLifecycleStatus;
import com.ftk.tpip.provider.domain.model.ProviderEndpoint;
import com.ftk.tpip.provider.domain.repository.ProviderEndpointRepository;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationRoutingAssetApplicationService {
    private final NotificationRoutingAssetRepository repository;
    private final CanonicalJsonService canonicalJson;
    private final ObjectMapper json;
    private final NotificationDeliveryProperties properties;
    private final NotificationTemplateRepository templates;
    private final NotificationProviderGovernance providers;
    private final ProviderEndpointRepository endpoints;

    @Autowired
    public NotificationRoutingAssetApplicationService(NotificationRoutingAssetRepository repository,
            CanonicalJsonService canonicalJson, ObjectMapper json, NotificationDeliveryProperties properties,
            NotificationTemplateRepository templates, NotificationProviderGovernance providers,
            ProviderEndpointRepository endpoints) {
        this.repository = repository;
        this.canonicalJson = canonicalJson;
        this.json = json;
        this.properties = properties;
        this.templates = templates;
        this.providers = providers;
        this.endpoints = endpoints;
    }

    NotificationRoutingAssetApplicationService(NotificationRoutingAssetRepository repository,
            CanonicalJsonService canonicalJson, ObjectMapper json, NotificationDeliveryProperties properties) {
        this(repository, canonicalJson, json, properties, null, new NotificationProviderGovernance(), null);
    }

    @Transactional
    public NotificationChannel createChannel(String code, String name, String environmentCode, String actor) {
        return repository.createChannel(new NotificationChannel(null, code(code, "channelCode"),
                required(name, "channelName", 200), environment(environmentCode), NotificationAssetStatus.ACTIVE,
                null, 0, null, null),
                actor(actor));
    }

    @Transactional(readOnly = true)
    public NotificationChannel channel(long id) { return repository.findChannel(id).orElseThrow(() -> missing("channel")); }
    @Transactional(readOnly = true)
    public List<NotificationChannel> channels() { return repository.findChannels(); }

    @Transactional
    public NotificationChannelVersion createChannelVersion(long channelId, NotificationProviderType providerType,
            String endpointUri, Long endpointRevisionId, String secretRef, JsonNode configuration,
            Long templateVersionId, String actor) {
        NotificationChannel channel = channel(channelId);
        String endpoint = endpointUri;
        if (endpointRevisionId != null) {
            if (endpoints == null) throw new IllegalStateException("ProviderEndpoint repository is unavailable");
            ProviderEndpoint revision = endpoints.findById(endpointRevisionId)
                    .orElseThrow(() -> new IllegalArgumentException("endpoint revision does not exist"));
            if (revision.lifecycleStatus() != EndpointLifecycleStatus.PUBLISHED
                    || !revision.environmentCode().equals(channel.environmentCode())) {
                throw new IllegalArgumentException("endpoint revision is unpublished or belongs to another environment");
            }
            if (revision.httpMethod() != EndpointHttpMethod.POST
                    || !"application/json".equalsIgnoreCase(revision.contentType())) {
                throw new IllegalArgumentException("notification endpoint revision must use POST application/json");
            }
            if (revision.credentialRefId() != null) {
                throw new IllegalArgumentException("notification endpoint revision must not bind a CredentialRef id");
            }
            endpoint = revision.baseUrl() + revision.resourcePath();
        }
        String resolvedEndpoint = endpoint(providerType, endpoint);
        String reference = secretRef(secretRef);
        JsonNode config = configuration == null || configuration.isNull() ? json.createObjectNode() : configuration;
        if (!config.isObject()) throw new IllegalArgumentException("configuration must be a JSON object");
        providers.validateChannel(providerType, config, reference);
        String canonicalConfig = canonicalJson.canonicalString(config);
        ObjectNode content = json.createObjectNode();
        content.put("providerType", providerType.name()).put("endpointUri", resolvedEndpoint);
        if (endpointRevisionId != null) content.put("endpointRevisionId", endpointRevisionId);
        if (reference != null) content.put("authorizationSecretRef", reference);
        content.set("configuration", canonicalJson.canonicalNode(config));
        if (templateVersionId != null) {
            NotificationTemplateVersion templateVersion = templates.findVersion(templateVersionId)
                    .orElseThrow(() -> new IllegalArgumentException("notification template version does not exist"));
            NotificationTemplate template = templates.find(templateVersion.templateId()).orElseThrow();
            if (templateVersion.lifecycleStatus() != NotificationAssetLifecycle.PUBLISHED) {
                throw new IllegalArgumentException("channel requires a PUBLISHED template version");
            }
            if (template.status() != NotificationAssetStatus.ACTIVE
                    || !template.environmentCode().equals(channel.environmentCode())) {
                throw new IllegalArgumentException("template is inactive or belongs to another environment");
            }
            if (templateVersion.providerType() != providerType) {
                throw new IllegalArgumentException("template providerType does not match channel providerType");
            }
            content.put("templateVersionId", templateVersionId);
        }
        return repository.createChannelVersion(new NotificationChannelVersion(null, channelId, 0, providerType,
                resolvedEndpoint, endpointRevisionId, reference, canonicalConfig, canonicalJson.sha256(canonicalJson.write(content)),
                templateVersionId,
                NotificationAssetLifecycle.DRAFT, null, null), actor(actor));
    }

    NotificationChannelVersion createChannelVersion(long channelId, NotificationProviderType providerType,
            String endpointUri, String secretRef, JsonNode configuration, String actor) {
        return createChannelVersion(channelId, providerType, endpointUri, null, secretRef, configuration, null, actor);
    }

    @Transactional(readOnly = true)
    public List<NotificationChannelVersion> channelVersions(long channelId) { channel(channelId); return repository.findChannelVersions(channelId); }
    @Transactional
    public NotificationChannelVersion publishChannelVersion(long channelId, long versionId, String actor) {
        channel(channelId); return repository.publishChannelVersion(channelId, versionId, actor(actor));
    }
    @Transactional
    public NotificationChannel changeChannelStatus(long channelId, NotificationAssetStatus status,
            long rowVersion, String actor) {
        channel(channelId);
        if (status == null) throw new IllegalArgumentException("status is required");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
        return repository.changeChannelStatus(channelId, status, rowVersion, actor(actor));
    }

    @Transactional
    public NotificationRoute createRoute(String code, String name, String environmentCode, String actor) {
        return repository.createRoute(new NotificationRoute(null, code(code, "routeCode"),
                required(name, "routeName", 200), environment(environmentCode), NotificationAssetStatus.ACTIVE,
                null, 0, null, null), actor(actor));
    }

    @Transactional(readOnly = true)
    public NotificationRoute route(long id) { return repository.findRoute(id).orElseThrow(() -> missing("route")); }
    @Transactional(readOnly = true)
    public List<NotificationRoute> routes() { return repository.findRoutes(); }

    @Transactional
    public NotificationRouteVersion createRouteVersion(long routeId, int priority, List<String> eventTypes,
            List<Long> channelVersionIds, String actor) {
        route(routeId);
        if (priority < 0 || priority > 10000) throw new IllegalArgumentException("priority must be between 0 and 10000");
        if (eventTypes == null || eventTypes.isEmpty() || eventTypes.size() > 100) {
            throw new IllegalArgumentException("eventTypes must contain 1 to 100 values");
        }
        List<String> events = eventTypes.stream().map(String::trim).distinct().sorted().toList();
        if (events.size() != eventTypes.size() || events.stream().anyMatch(v -> !("*".equals(v)
                || v.matches("[A-Z][A-Z0-9_]{1,99}")))) {
            throw new IllegalArgumentException("eventTypes are invalid or duplicated");
        }
        if (channelVersionIds == null || channelVersionIds.isEmpty() || channelVersionIds.size() > 20) {
            throw new IllegalArgumentException("channelVersionIds must contain 1 to 20 values");
        }
        List<Long> channels = channelVersionIds.stream().distinct().sorted().toList();
        if (channels.size() != channelVersionIds.size() || channels.stream().anyMatch(v -> v == null || v <= 0)) {
            throw new IllegalArgumentException("channelVersionIds are invalid or duplicated");
        }
        ArrayNode eventArray = json.valueToTree(events);
        ArrayNode channelArray = json.valueToTree(channels);
        ObjectNode content = json.createObjectNode().put("priority", priority);
        content.set("eventTypes", eventArray); content.set("channelVersionIds", channelArray);
        return repository.createRouteVersion(new NotificationRouteVersion(null, routeId, 0, priority,
                canonicalJson.write(eventArray), canonicalJson.write(channelArray),
                canonicalJson.sha256(canonicalJson.write(content)), NotificationAssetLifecycle.DRAFT,
                null, null), actor(actor));
    }

    @Transactional(readOnly = true)
    public List<NotificationRouteVersion> routeVersions(long routeId) { route(routeId); return repository.findRouteVersions(routeId); }
    @Transactional
    public NotificationRouteVersion publishRouteVersion(long routeId, long versionId, String actor) {
        route(routeId); return repository.publishRouteVersion(routeId, versionId, actor(actor));
    }
    @Transactional
    public NotificationRoute changeRouteStatus(long routeId, NotificationAssetStatus status,
            long rowVersion, String actor) {
        route(routeId);
        if (status == null) throw new IllegalArgumentException("status is required");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
        return repository.changeRouteStatus(routeId, status, rowVersion, actor(actor));
    }

    private String endpoint(NotificationProviderType provider, String value) {
        String normalized = required(value, "endpointUri", 1000);
        URI uri;
        try { uri = URI.create(normalized); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("endpointUri is invalid", exception); }
        boolean scheme = "https".equalsIgnoreCase(uri.getScheme())
                || (properties.isAllowHttpChannelEndpoints() && "http".equalsIgnoreCase(uri.getScheme()));
        if (!scheme || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null) throw new IllegalArgumentException("endpointUri is not allowed");
        if (provider == NotificationProviderType.WECOM && !"qyapi.weixin.qq.com".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("WECOM endpointUri host is not allowed");
        }
        if (provider == NotificationProviderType.DINGTALK && !"oapi.dingtalk.com".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("DINGTALK endpointUri host is not allowed");
        }
        return uri.normalize().toString();
    }

    private static String secretRef(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!normalized.matches("env://TPIP_SECRET_[A-Z0-9_]{1,200}")) {
            throw new IllegalArgumentException("authorizationSecretRef must use an allowed env reference");
        }
        return normalized;
    }
    private static String code(String value, String field) {
        String result = required(value, field, 100);
        if (!result.matches("[a-z][a-z0-9.-]{1,99}")) throw new IllegalArgumentException(field + " is invalid");
        return result;
    }
    private static String actor(String value) { return required(value, "X-Operator", 100); }
    private static String environment(String value) {
        String result = required(value, "environmentCode", 32);
        if (!result.matches("[a-z][a-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("environmentCode is invalid");
        }
        return result;
    }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException(field + " is too long");
        return result;
    }
    private static IllegalArgumentException missing(String type) { return new IllegalArgumentException("notification " + type + " does not exist"); }
}
