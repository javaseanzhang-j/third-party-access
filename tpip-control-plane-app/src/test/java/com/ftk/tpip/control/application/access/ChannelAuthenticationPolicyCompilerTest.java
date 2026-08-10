package com.ftk.tpip.control.application.access;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.access.domain.model.*;
import com.ftk.tpip.provider.domain.model.*;
import com.ftk.tpip.shared.AssetCode;
import com.ftk.tpip.shared.SemanticVersion;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ChannelAuthenticationPolicyCompilerTest {
    @Test void compilesApiKeyWithSecretReferenceWithoutReadingSecretMaterial() throws Exception {
        var json = new ObjectMapper(); var compiler = new ChannelAuthenticationPolicyCompiler(json); Instant now = Instant.now();
        var template = new AuthenticationTemplate(1L, null, AssetCode.of("generic.api-key"),
                "通用 API Key", "API_KEY", "builtin.auth.api-key@1.0.0", null,
                AccessChannelStatus.ACTIVE, now);
        var version = new AuthenticationTemplateVersion(2L, 1, 1, new SemanticVersion(1, 0, 0),
                "{}", "{}", "{\"policyType\":\"builtin.auth.api-key@1.0.0\",\"secretField\":\"secret\"}",
                "a".repeat(64), AccessPolicyLifecycleStatus.PUBLISHED, now, now);
        var profile = new CredentialProfile(3L, 10, AssetCode.of("aliyun.sms.account"),
                "阿里云短信账号", "ACCESS_KEY", null, AccessChannelStatus.ACTIVE, 0, now, now);
        var item = CredentialProfileItem.secretRef(3, AssetCode.of("secret"), "Secret", 4, null);
        var secret = new CredentialRef(4L, 10, AssetCode.of("aliyun.sms.secret"), "test",
                CredentialType.API_KEY, "env://TPIP_SECRET_ALIYUN_SMS", null,
                CredentialStatus.ACTIVE, 0, now, now);

        var result = compiler.compile(template, version, profile, List.of(item), Map.of(4L, secret),
                json.readTree("{\"headerName\":\"X-API-Key\"}"));

        assertEquals("env://TPIP_SECRET_ALIYUN_SMS", result.at("/stages/BEFORE_TRANSPORT/0/with/secretRef").textValue());
        assertEquals("builtin.auth.api-key@1.0.0", result.at("/stages/BEFORE_TRANSPORT/0/use").textValue());
        assertFalse(result.toString().contains("plaintext"));
    }
}
