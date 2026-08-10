package com.ftk.tpip.control.application.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.CompiledNotificationTemplate;
import com.ftk.tpip.release.domain.model.NotificationAssetLifecycle;
import com.ftk.tpip.release.domain.model.NotificationAssetStatus;
import com.ftk.tpip.release.domain.model.NotificationProviderType;
import com.ftk.tpip.release.domain.model.NotificationTemplate;
import com.ftk.tpip.release.domain.model.NotificationTemplateVersion;
import com.ftk.tpip.release.domain.repository.NotificationTemplateRepository;
import com.ftk.tpip.release.domain.service.NotificationTemplateEngine;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationTemplateApplicationService {
    private final NotificationTemplateRepository repository;
    private final NotificationTemplateEngine engine;
    private final CanonicalJsonService canonicalJson;
    private final NotificationProviderGovernance providers;

    public NotificationTemplateApplicationService(NotificationTemplateRepository repository,
            NotificationTemplateEngine engine, CanonicalJsonService canonicalJson,
            NotificationProviderGovernance providers) {
        this.repository = repository; this.engine = engine; this.canonicalJson = canonicalJson;
        this.providers = providers;
    }

    @Transactional
    public NotificationTemplate create(String code, String name, String environmentCode, String actor) {
        return repository.create(new NotificationTemplate(null, code(code), required(name, "templateName", 200),
                environment(environmentCode), NotificationAssetStatus.ACTIVE, null, 0, null, null), actor(actor));
    }

    @Transactional(readOnly = true)
    public NotificationTemplate get(long id) {
        return repository.find(id).orElseThrow(() -> new IllegalArgumentException("notification template does not exist"));
    }
    @Transactional(readOnly = true) public List<NotificationTemplate> list() { return repository.findAll(); }

    @Transactional
    public NotificationTemplateVersion createVersion(long templateId, NotificationProviderType providerType,
            String contentType, JsonNode templateDocument, JsonNode variableSchema, String actor) {
        get(templateId);
        if (templateDocument == null || variableSchema == null) {
            throw new IllegalArgumentException("templateDocument and variableSchema are required");
        }
        providers.validateTemplate(providerType, templateDocument, contentType);
        CompiledNotificationTemplate compiled = engine.compile(canonicalJson.write(templateDocument),
                canonicalJson.write(variableSchema));
        ObjectNode content = new ObjectMapper().createObjectNode();
        content.put("providerType", providerType.name()).put("contentType", contentType)
                .put("templateDocument", compiled.templateDocument())
                .put("variableSchema", compiled.variableSchema());
        return repository.createVersion(new NotificationTemplateVersion(null, templateId, 0, providerType,
                contentType, compiled.templateDocument(), compiled.variableSchema(), compiled.referencedVariables(),
                canonicalJson.sha256(canonicalJson.write(content)), NotificationAssetLifecycle.DRAFT, null, null),
                actor(actor));
    }

    @Transactional(readOnly = true)
    public List<NotificationTemplateVersion> versions(long templateId) { get(templateId); return repository.findVersions(templateId); }
    @Transactional
    public NotificationTemplateVersion publish(long templateId, long versionId, String actor) {
        get(templateId);
        NotificationTemplateVersion version = repository.findVersion(templateId, versionId)
                .orElseThrow(() -> new IllegalArgumentException("notification template version does not exist"));
        engine.compile(version.templateDocument(), version.variableSchema());
        return repository.publish(templateId, versionId, actor(actor));
    }
    @Transactional
    public NotificationTemplate changeStatus(long templateId, NotificationAssetStatus status,
            long rowVersion, String actor) {
        get(templateId);
        if (status == null) throw new IllegalArgumentException("status is required");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
        return repository.changeStatus(templateId, status, rowVersion, actor(actor));
    }

    private static String code(String value) {
        String result = required(value, "templateCode", 100);
        if (!result.matches("[a-z][a-z0-9.-]{1,99}")) throw new IllegalArgumentException("templateCode is invalid");
        return result;
    }
    private static String environment(String value) {
        String result = required(value, "environmentCode", 32);
        if (!result.matches("[a-z][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("environmentCode is invalid");
        return result;
    }
    private static String actor(String value) { return required(value, "X-Operator", 100); }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException(field + " is too long");
        return result;
    }
}
