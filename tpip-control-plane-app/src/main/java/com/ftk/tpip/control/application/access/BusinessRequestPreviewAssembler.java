package com.ftk.tpip.control.application.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.access.domain.model.*;
import com.ftk.tpip.provider.domain.model.*;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds a human-readable preview without resolving Secret material. */
@Component
public class BusinessRequestPreviewAssembler {
    private final ObjectMapper json;
    public BusinessRequestPreviewAssembler(ObjectMapper json) { this.json = json; }

    public Preview assemble(AccessChannel channel, ProviderContract contract,
            InterfaceTransportVersion transport, ChannelAuthenticationVersion authentication,
            AuthenticationTemplate template, AuthenticationTemplateVersion templateVersion,
            CredentialProfile profile, List<CredentialProfileItem> items,
            Map<Long, CredentialRef> secretRefs) {
        JsonNode policy = read(authentication.compiledPolicyDocument(), "compiled authentication policy");
        JsonNode configuration = read(authentication.configurationDocument(), "authentication configuration");
        JsonNode metadata = transport.transportMetadata() == null
                ? json.createObjectNode() : read(transport.transportMetadata(), "transport metadata");
        List<CredentialField> fields = items.stream().map(item -> credentialField(item, secretRefs)).toList();
        JsonNode step = policy.at("/stages/BEFORE_TRANSPORT/0");
        String headerName = text(step.at("/with/headerName"));
        var effects = headerName == null ? List.<AuthenticationEffect>of() : List.of(
                new AuthenticationEffect("HEADER", headerName,
                        "运行时由认证策略计算并注入；预览不读取 Secret 明文", text(step.get("use"))));
        return new Preview(
                new Target(channel.id(), channel.channelName(), contract.id(), contract.contractName(),
                        transport.id(), transport.semanticVersion().toString(), join(channel.baseUrl(), transport.resourcePath()),
                        transport.httpMethod().name(), transport.contentType(), transport.charsetName(),
                        transport.connectTimeoutMs(), transport.readTimeoutMs(), transport.totalTimeoutMs(), metadata),
                new Authentication(authentication.id(), authentication.versionNo(), template.templateName(),
                        template.templateType(), templateVersion.semanticVersion().toString(), profile.profileName(),
                        configuration, fields, effects),
                "READY", "已使用发布版本生成预览；Secret 只显示引用，不读取明文");
    }

    static String join(String baseUrl, String resourcePath) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + resourcePath
                : baseUrl + resourcePath;
    }
    private CredentialField credentialField(CredentialProfileItem item, Map<Long, CredentialRef> refs) {
        if (item.valueSource() == CredentialValueSource.SECRET_REF) {
            CredentialRef ref = refs.get(item.secretRefId());
            if (ref == null) throw new IllegalArgumentException("Secret reference does not exist");
            return new CredentialField(item.fieldCode().value(), item.fieldName(), "SECRET_REF",
                    "Secret 引用：" + ref.credentialCode().value() + "（未读取明文）", true);
        }
        return new CredentialField(item.fieldCode().value(), item.fieldName(), "PUBLIC_VALUE",
                item.sensitive() ? "******" : item.publicValue(), item.sensitive());
    }
    private JsonNode read(String value, String label) {
        try { return json.readTree(value); }
        catch (Exception failure) { throw new IllegalStateException("Stored " + label + " JSON is invalid", failure); }
    }
    private static String text(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull() || !value.isTextual()
                ? null : value.textValue();
    }

    public record Preview(Target target, Authentication authentication, String readiness, String notice) {}
    public record Target(long channelId, String channelName, long interfaceId, String interfaceName,
            long transportVersionId, String transportVersion, String finalUrl, String httpMethod,
            String contentType, String charsetName, Integer connectTimeoutMs, Integer readTimeoutMs,
            Integer totalTimeoutMs, JsonNode transportMetadata) {}
    public record Authentication(long authenticationVersionId, int versionNo, String templateName,
            String templateType, String templateVersion, String credentialProfileName,
            JsonNode configuration, List<CredentialField> credentialFields,
            List<AuthenticationEffect> effects) {}
    public record CredentialField(String fieldCode, String fieldName, String sourceType,
            String displayValue, boolean sensitive) {}
    public record AuthenticationEffect(String target, String name, String description, String policyType) {}
}
