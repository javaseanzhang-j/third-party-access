package com.ftk.tpip.control.application.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.catalog.domain.model.CanonicalContract;
import com.ftk.tpip.catalog.domain.model.CanonicalContractLifecycleStatus;
import com.ftk.tpip.catalog.domain.model.CanonicalContractQuery;
import com.ftk.tpip.catalog.domain.model.CanonicalContractStatus;
import com.ftk.tpip.catalog.domain.model.CanonicalContractVersion;
import com.ftk.tpip.catalog.domain.model.CompatibilityMode;
import com.ftk.tpip.catalog.domain.model.ContractKind;
import com.ftk.tpip.catalog.domain.model.SchemaStandard;
import com.ftk.tpip.catalog.domain.repository.CanonicalContractRepository;
import com.ftk.tpip.mcp.domain.model.McpToolAsset;
import com.ftk.tpip.mcp.domain.model.McpToolVersion;
import com.ftk.tpip.mcp.domain.repository.McpToolAssetRepository;
import com.ftk.tpip.shared.AssetCode;
import com.ftk.tpip.shared.SemanticVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class McpToolContractImpactServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");
    private static final String SHA = "a".repeat(64);

    @Test
    void reportsBreakingRequestAndAdditiveResponseChanges() {
        String toolInput = """
                {"type":"object","required":["mobile"],"properties":{"mobile":{"type":"string"}}}
                """;
        String toolOutput = """
                {"type":"object","properties":{"messageId":{"type":"string"}}}
                """;
        String currentInput = """
                {"type":"object","required":["mobile","templateCode"],"properties":{"mobile":{"type":"string"},"templateCode":{"type":"string"}}}
                """;
        String currentOutput = """
                {"type":"object","properties":{"messageId":{"type":"string"},"status":{"type":"string"}}}
                """;
        var service = new McpToolContractImpactService(new Tools(toolInput, toolOutput),
                new Contracts(currentInput, currentOutput), new ObjectMapper());

        var impact = service.analyze(1, 11);

        assertEquals(McpToolContractImpactService.ImpactLevel.BREAKING, impact.level());
        assertTrue(impact.changes().stream().anyMatch(value -> value.path().equals("$.templateCode")
                && value.level() == McpToolContractImpactService.ChangeLevel.BREAKING));
        assertTrue(impact.changes().stream().anyMatch(value -> value.path().equals("$.status")
                && value.level() == McpToolContractImpactService.ChangeLevel.ADDITIVE));
        assertEquals("1.1.0", impact.requestContract().semanticVersion());
    }

    private static final class Tools implements McpToolAssetRepository {
        private final McpToolAsset tool = new McpToolAsset(1L, 9, "send_sms", "发送短信", "发送业务短信",
                "owner", McpToolAsset.Status.ACTIVE, 0, NOW, NOW);
        private final McpToolVersion version;
        private Tools(String input, String output) {
            version = new McpToolVersion(11L, 1, 1, "发送短信", "发送业务短信", null, input, output,
                    false, false, false, true, McpToolVersion.ConfirmationMode.NONE, SHA,
                    McpToolVersion.LifecycleStatus.PUBLISHED, "sean", NOW, "sean", NOW);
        }
        @Override public List<McpToolAsset> findAll() { return List.of(tool); }
        @Override public Optional<McpToolAsset> findById(long id) { return id == 1 ? Optional.of(tool) : Optional.empty(); }
        @Override public McpToolAsset create(McpToolAsset value, String actor) { throw new UnsupportedOperationException(); }
        @Override public List<McpToolVersion> findVersions(long toolId) { return List.of(version); }
        @Override public Optional<McpToolVersion> findVersion(long toolId, long versionId) { return versionId == 11 ? Optional.of(version) : Optional.empty(); }
        @Override public McpToolVersion createVersion(McpToolVersion value, String actor) { throw new UnsupportedOperationException(); }
        @Override public McpToolVersion publishVersion(long toolId, long versionId, String actor) { throw new UnsupportedOperationException(); }
        @Override public List<PublishedTool> findPublishedTools() { return List.of(); }
    }

    private static final class Contracts implements CanonicalContractRepository {
        private final CanonicalContract request = new CanonicalContract(21L, 9,
                AssetCode.of("notification.sms.send.request"), "短信请求", ContractKind.REQUEST, null,
                CanonicalContractStatus.ACTIVE, 0, NOW, NOW);
        private final CanonicalContract response = new CanonicalContract(22L, 9,
                AssetCode.of("notification.sms.send.response"), "短信返回", ContractKind.RESPONSE, null,
                CanonicalContractStatus.ACTIVE, 0, NOW, NOW);
        private final CanonicalContractVersion requestVersion;
        private final CanonicalContractVersion responseVersion;
        private Contracts(String input, String output) {
            requestVersion = version(31, 21, input);
            responseVersion = version(32, 22, output);
        }
        private static CanonicalContractVersion version(long id, long contractId, String schema) {
            return new CanonicalContractVersion(id, contractId, 2, new SemanticVersion(1, 1, 0),
                    SchemaStandard.JSON_SCHEMA_2020_12, schema, null, CompatibilityMode.BACKWARD, SHA,
                    CanonicalContractLifecycleStatus.PUBLISHED, NOW, NOW);
        }
        @Override public Optional<CanonicalContract> findById(long id) { return List.of(request, response).stream().filter(value -> value.id() == id).findFirst(); }
        @Override public Optional<CanonicalContract> findByCode(AssetCode code) { return Optional.empty(); }
        @Override public List<CanonicalContract> findAll(CanonicalContractQuery query) { return List.of(request, response).stream().filter(value -> value.contractKind() == query.contractKind()).toList(); }
        @Override public long count(CanonicalContractQuery query) { return findAll(query).size(); }
        @Override public CanonicalContract create(CanonicalContract contract, String actor) { throw new UnsupportedOperationException(); }
        @Override public CanonicalContract update(CanonicalContract contract, String actor) { throw new UnsupportedOperationException(); }
        @Override public Optional<CanonicalContractVersion> findVersion(long contractId, long versionId) { return findVersionById(versionId); }
        @Override public Optional<CanonicalContractVersion> findVersionById(long versionId) { return List.of(requestVersion, responseVersion).stream().filter(value -> value.id() == versionId).findFirst(); }
        @Override public List<CanonicalContractVersion> findVersions(long contractId) { return contractId == 21 ? List.of(requestVersion) : List.of(responseVersion); }
        @Override public CanonicalContractVersion createVersion(CanonicalContractVersion version, String actor) { throw new UnsupportedOperationException(); }
        @Override public CanonicalContractVersion publishVersion(long contractId, long versionId, String actor) { throw new UnsupportedOperationException(); }
    }
}
