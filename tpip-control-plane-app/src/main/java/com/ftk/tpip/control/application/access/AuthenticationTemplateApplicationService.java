package com.ftk.tpip.control.application.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.access.domain.model.AuthenticationTemplate;
import com.ftk.tpip.access.domain.model.AuthenticationTemplateVersion;
import com.ftk.tpip.access.domain.repository.AuthenticationTemplateRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationTemplateApplicationService {
    private final AuthenticationTemplateRepository templates;
    private final ObjectMapper json;
    public AuthenticationTemplateApplicationService(AuthenticationTemplateRepository templates, ObjectMapper json) {
        this.templates = templates; this.json = json;
    }
    @Transactional(readOnly = true)
    public List<TemplateView> list(Long providerId) {
        return templates.findAll(providerId).stream().map(value -> new TemplateView(value,
                templates.findVersions(value.id()).stream().map(this::version).toList())).toList();
    }
    @Transactional(readOnly = true)
    public TemplateVersionView version(long templateId, long versionId) {
        templates.findById(templateId).orElseThrow(() -> new IllegalArgumentException("authentication template does not exist"));
        return version(templates.findVersionById(templateId, versionId)
                .orElseThrow(() -> new IllegalArgumentException("authentication template version does not exist")));
    }
    private TemplateVersionView version(AuthenticationTemplateVersion value) {
        return new TemplateVersionView(value.id(), value.versionNo(), value.semanticVersion().toString(),
                read(value.credentialSchema()), read(value.configurationSchema()), read(value.templateDocument()),
                value.contentChecksum(), value.lifecycleStatus().name(), value.publishedAt(), value.createdAt());
    }
    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (Exception failure) { throw new IllegalStateException("Stored authentication template JSON is invalid", failure); }
    }
    public record TemplateView(AuthenticationTemplate template, List<TemplateVersionView> versions) {}
    public record TemplateVersionView(long id, int versionNo, String semanticVersion, JsonNode credentialSchema,
            JsonNode configurationSchema, JsonNode templateDocument, String contentChecksum,
            String lifecycleStatus, java.time.Instant publishedAt, java.time.Instant createdAt) {}
}
