package com.ftk.tpip.control.application.access;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.access.domain.model.*;
import com.ftk.tpip.provider.domain.model.*;
import com.ftk.tpip.shared.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class BusinessRequestPreviewAssemblerTest {
    @Test void createsHumanReadablePreviewAndNeverExposesSecretMaterial() {
        Instant now = Instant.now(); ObjectMapper json = new ObjectMapper();
        var channel = new AccessChannel(1L, 10, AssetCode.of("aliyun.sms.default"), "阿里云短信默认通道",
                "https://dysmsapi.aliyuncs.com", null, null, AccessChannelStatus.ACTIVE, 0, now, now);
        var contract = new ProviderContract(2L, 10, AssetCode.of("aliyun.sms.send"), "发送短信",
                ProtocolType.HTTP, null, ContractStatus.ACTIVE, 0, now, now);
        var transport = new InterfaceTransportVersion(3L, 2, 1, new SemanticVersion(1, 0, 0),
                "/", EndpointHttpMethod.POST, "application/json", "UTF-8", 1000, 3000, 5000,
                "{}", "a".repeat(64), EndpointLifecycleStatus.PUBLISHED, now, now);
        var auth = new ChannelAuthenticationVersion(4L, 1, 1, 5, 6, """
                {"headerName":"Authorization","credentialBindings":[
                  {"fieldCode":"access-key-id","location":"QUERY","parameterName":"AccessKeyId"}
                ]}
                """,
                "{\"stages\":{\"BEFORE_TRANSPORT\":[{\"use\":\"builtin.auth.api-key@1.0.0\",\"with\":{\"headerName\":\"Authorization\",\"secretRef\":\"env://SECRET\"}}]}}",
                "1", "b".repeat(64), AccessPolicyLifecycleStatus.PUBLISHED, now, now);
        var template = new AuthenticationTemplate(7L, null, AssetCode.of("generic.api-key"), "API Key 认证",
                "API_KEY", "builtin.auth.api-key@1.0.0", null, AccessChannelStatus.ACTIVE, now);
        var templateVersion = new AuthenticationTemplateVersion(5L, 7, 1, new SemanticVersion(1, 0, 0),
                "{}", "{}", "{}", "c".repeat(64), AccessPolicyLifecycleStatus.PUBLISHED, now, now);
        var profile = new CredentialProfile(6L, 10, AssetCode.of("aliyun.sms.account"), "阿里云短信账号",
                "ACCESS_KEY", null, AccessChannelStatus.ACTIVE, 0, now, now);
        var publicItem = CredentialProfileItem.publicValue(6, AssetCode.of("access-key-id"),
                "AccessKey ID", "LTAI-DEMO", false, null);
        var secretItem = CredentialProfileItem.secretRef(6, AssetCode.of("access-key-secret"), "AccessKey Secret", 8, null);
        var ref = new CredentialRef(8L, 10, AssetCode.of("aliyun.sms.access-key-secret"), "local",
                CredentialType.API_KEY, "env://SECRET", null, CredentialStatus.ACTIVE, 0, now, now);

        var preview = new BusinessRequestPreviewAssembler(json, new CredentialBindingResolver()).assemble(channel, contract, transport,
                auth, template, templateVersion, profile, List.of(publicItem, secretItem), Map.of(8L, ref));

        assertEquals("https://dysmsapi.aliyuncs.com/", preview.target().finalUrl());
        assertEquals("AccessKeyId", preview.authentication().effects().getFirst().name());
        assertEquals("QUERY", preview.authentication().effects().getFirst().target());
        assertEquals("Authorization", preview.authentication().effects().get(1).name());
        assertTrue(preview.authentication().credentialFields().get(1).displayValue().contains("未读取明文"));
        assertFalse(preview.toString().contains("env://SECRET"));
    }
}
