package com.ftk.tpip.control.application.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.catalog.domain.model.CanonicalOperation;
import com.ftk.tpip.catalog.domain.model.CanonicalOperationQuery;
import com.ftk.tpip.catalog.domain.model.DataClassification;
import com.ftk.tpip.catalog.domain.model.IdempotencyClass;
import com.ftk.tpip.catalog.domain.model.InvocationMode;
import com.ftk.tpip.catalog.domain.model.OperationStatus;
import com.ftk.tpip.catalog.domain.repository.CanonicalOperationRepository;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.mcp.domain.model.McpToolAsset;
import com.ftk.tpip.mcp.domain.model.McpToolVersion;
import com.ftk.tpip.mcp.domain.repository.McpToolAssetRepository;
import com.ftk.tpip.shared.AssetCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class McpToolAssetApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final FakeTools tools = new FakeTools();
    private final McpToolAssetApplicationService service = new McpToolAssetApplicationService(
            tools, new Operations(), new CanonicalJsonService(json), json, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsValidatesPublishesAndExposesLatestToolSnapshot() {
        var detail = service.createTool(9, "send_business_sms", "发送业务短信",
                "由平台选择短信通道并发送", "owner", "sean");
        McpToolVersion version = service.createVersion(detail.summary().tool().id(),
                new McpToolAssetApplicationService.CreateVersion("发送业务短信", "发送验证码或通知",
                        "login", json.createObjectNode().put("type", "object"),
                        json.createObjectNode().put("type", "object"), false, false,
                        false, true, McpToolVersion.ConfirmationMode.NONE), "sean");

        assertTrue(service.validate(1, version.id()).ready());
        assertEquals(McpToolVersion.LifecycleStatus.PUBLISHED,
                service.publish(1, version.id(), "sean").lifecycleStatus());
        var snapshot = service.publishedSnapshot();
        assertEquals("tpip.mcp-tools/v1", snapshot.apiVersion());
        assertEquals("notification.sms.send", snapshot.tools().getFirst().serviceCode());
    }

    @Test
    void rejectsUnsafeDestructiveVersionAndNonObjectSchema() {
        service.createTool(9, "delete_remote_data", "删除远程数据", "危险操作", "owner", "sean");
        assertThrows(IllegalArgumentException.class, () -> service.createVersion(1,
                new McpToolAssetApplicationService.CreateVersion("删除数据", "删除外部数据", null,
                        json.createArrayNode(), null, false, true, false, true,
                        McpToolVersion.ConfirmationMode.NONE), "sean"));

        McpToolVersion unsafe = service.createVersion(1,
                new McpToolAssetApplicationService.CreateVersion("删除数据", "删除外部数据", null,
                        json.createObjectNode().put("type", "object"), null, false, true,
                        false, true, McpToolVersion.ConfirmationMode.NONE), "sean");
        assertFalse(service.validate(1, unsafe.id()).ready());
        assertThrows(IllegalArgumentException.class, () -> service.publish(1, unsafe.id(), "sean"));
    }

    private static final class Operations implements CanonicalOperationRepository {
        private final CanonicalOperation operation = new CanonicalOperation(9L, 2,
                AssetCode.of("notification.sms.send"), "发送业务短信", "标准短信服务",
                InvocationMode.SYNC, IdempotencyClass.NON_IDEMPOTENT, DataClassification.INTERNAL,
                "owner", OperationStatus.ACTIVE, 0, NOW, NOW);
        @Override public Optional<CanonicalOperation> findById(long id) { return id == 9 ? Optional.of(operation) : Optional.empty(); }
        @Override public Optional<CanonicalOperation> findByCode(AssetCode code) { return Optional.of(operation); }
        @Override public List<CanonicalOperation> findAll(CanonicalOperationQuery query) { return List.of(operation); }
        @Override public long count(CanonicalOperationQuery query) { return 1; }
        @Override public boolean capabilityIsActive(long capabilityId) { return true; }
        @Override public CanonicalOperation create(CanonicalOperation value, String actor) { throw new UnsupportedOperationException(); }
        @Override public CanonicalOperation update(CanonicalOperation value, String actor) { throw new UnsupportedOperationException(); }
    }

    private static final class FakeTools implements McpToolAssetRepository {
        private McpToolAsset asset;
        private final List<McpToolVersion> versions = new ArrayList<>();
        @Override public List<McpToolAsset> findAll() { return asset == null ? List.of() : List.of(asset); }
        @Override public Optional<McpToolAsset> findById(long id) { return asset != null && id == 1 ? Optional.of(asset) : Optional.empty(); }
        @Override public McpToolAsset create(McpToolAsset value, String actor) {
            asset = new McpToolAsset(1L, value.operationId(), value.toolName(), value.displayName(),
                    value.description(), value.ownerCode(), value.status(), 0, NOW, NOW);
            return asset;
        }
        @Override public List<McpToolVersion> findVersions(long toolId) { return versions.reversed(); }
        @Override public Optional<McpToolVersion> findVersion(long toolId, long versionId) {
            return versions.stream().filter(value -> value.id() == versionId).findFirst();
        }
        @Override public McpToolVersion createVersion(McpToolVersion value, String actor) {
            McpToolVersion stored = new McpToolVersion((long) versions.size() + 1, value.toolId(),
                    versions.size() + 1, value.title(), value.description(), value.fixedScenario(),
                    value.inputSchema(), value.outputSchema(), value.readOnly(), value.destructive(),
                    value.idempotent(), value.openWorld(), value.confirmationMode(), value.contentChecksum(),
                    value.lifecycleStatus(), null, null, actor, NOW);
            versions.add(stored);
            return stored;
        }
        @Override public McpToolVersion publishVersion(long toolId, long versionId, String actor) {
            McpToolVersion old = findVersion(toolId, versionId).orElseThrow();
            McpToolVersion published = new McpToolVersion(old.id(), old.toolId(), old.versionNo(), old.title(),
                    old.description(), old.fixedScenario(), old.inputSchema(), old.outputSchema(), old.readOnly(),
                    old.destructive(), old.idempotent(), old.openWorld(), old.confirmationMode(), old.contentChecksum(),
                    McpToolVersion.LifecycleStatus.PUBLISHED, actor, NOW, old.createdBy(), old.createdAt());
            versions.set(versions.indexOf(old), published);
            return published;
        }
        @Override public List<PublishedTool> findPublishedTools() {
            return versions.stream().filter(value -> value.lifecycleStatus() == McpToolVersion.LifecycleStatus.PUBLISHED)
                    .reduce((first, second) -> second).map(value -> List.of(new PublishedTool(1,
                            asset.toolName(), asset.displayName(), 9, "notification.sms.send", value.id(),
                            value.versionNo(), value.title(), value.description(), value.fixedScenario(),
                            value.inputSchema(), value.outputSchema(), value.readOnly(), value.destructive(),
                            value.idempotent(), value.openWorld(), value.confirmationMode(), value.contentChecksum(),
                            value.publishedBy(), value.publishedAt()))).orElseGet(List::of);
        }
    }
}
