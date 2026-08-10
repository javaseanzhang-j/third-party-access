package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.integration.domain.model.BindingStatus;
import com.ftk.tpip.integration.domain.model.MappingAssetDirection;
import com.ftk.tpip.integration.domain.repository.IntegrationBindingRepository;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.FixtureSuiteRepository;
import com.ftk.tpip.shared.AssetCode;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FixtureSuiteApplicationService {
    private final FixtureSuiteRepository suites;
    private final IntegrationBindingRepository bindings;
    private final CanonicalJsonService canonicalJson;
    private final ObjectMapper json;
    private final FixtureAssertionEvaluator assertions;

    public FixtureSuiteApplicationService(FixtureSuiteRepository suites, IntegrationBindingRepository bindings,
            CanonicalJsonService canonicalJson, ObjectMapper json, FixtureAssertionEvaluator assertions) {
        this.suites = suites; this.bindings = bindings; this.canonicalJson = canonicalJson; this.json = json;
        this.assertions = assertions;
    }

    @Transactional
    public FixtureSuite create(long bindingId, String code, String name, String description, String actor) {
        var binding = bindings.findById(bindingId)
                .orElseThrow(() -> new IllegalArgumentException("bindingId does not exist: " + bindingId));
        if (binding.status() != BindingStatus.ACTIVE) throw new IllegalArgumentException("bindingId must reference an ACTIVE binding");
        AssetCode suiteCode = AssetCode.of(code);
        if (suites.findByCode(suiteCode).isPresent()) throw new IllegalArgumentException("FixtureSuite code already exists: " + code);
        return suites.create(FixtureSuite.create(bindingId, suiteCode, name, description), actor(actor));
    }

    @Transactional(readOnly = true)
    public FixtureSuite get(long id) {
        return suites.findById(id).orElseThrow(() -> new IllegalArgumentException("FixtureSuite does not exist: " + id));
    }

    @Transactional(readOnly = true)
    public List<FixtureSuite> list(long bindingId) { return suites.findByBinding(bindingId); }

    @Transactional
    public FixtureSuiteVersion createVersion(long suiteId, List<FixtureCaseInput> inputs, String actor) {
        FixtureSuite suite = get(suiteId);
        if (suite.status() != FixtureSuiteStatus.ACTIVE) throw new IllegalArgumentException("FixtureSuite must be ACTIVE");
        if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("FixtureSuite version requires at least one case");
        if (inputs.size() > 500) throw new IllegalArgumentException("FixtureSuite version supports at most 500 cases");
        var codes = new HashSet<String>();
        List<FixtureCase> cases = inputs.stream().map(input -> fixture(input, codes)).toList();
        String checksum = canonicalJson.sha256(canonicalJson.write(content(cases)));
        return suites.createVersion(FixtureSuiteVersion.draft(suiteId, checksum, cases), actor(actor));
    }

    @Transactional(readOnly = true)
    public List<FixtureSuiteVersion> versions(long suiteId) { get(suiteId); return suites.findVersions(suiteId); }

    @Transactional(readOnly = true)
    public FixtureSuiteVersion version(long suiteId, long versionId) {
        get(suiteId);
        return suites.findVersion(suiteId, versionId)
                .orElseThrow(() -> new IllegalArgumentException("FixtureSuiteVersion does not exist: " + versionId));
    }

    @Transactional
    public FixtureSuiteVersion publish(long suiteId, long versionId, String actor) {
        FixtureSuite suite = get(suiteId);
        if (suite.status() != FixtureSuiteStatus.ACTIVE) throw new IllegalArgumentException("FixtureSuite must be ACTIVE");
        version(suiteId, versionId);
        return suites.publishVersion(suiteId, versionId, actor(actor));
    }

    private FixtureCase fixture(FixtureCaseInput input, HashSet<String> codes) {
        if (input == null) throw new IllegalArgumentException("FixtureCase must not be null");
        if (input.direction() != MappingAssetDirection.OUTBOUND_REQUEST
                && input.direction() != MappingAssetDirection.INBOUND_RESPONSE) {
            throw new IllegalArgumentException("FixtureCase direction must be OUTBOUND_REQUEST or INBOUND_RESPONSE");
        }
        String source = canonicalJson.canonicalString(requiredObject(input.source(), "source"));
        String expected = input.expected() == null || input.expected().isNull() ? null
                : canonicalJson.canonicalString(requiredObject(input.expected(), "expected"));
        FixtureExecutionMode executionMode = input.executionMode() == null ? FixtureExecutionMode.MAPPING : input.executionMode();
        JsonNode assertionDocument = executionMode == FixtureExecutionMode.MAPPING
                ? assertions.validateForMapping(input.assertions())
                : assertions.validateAndCanonicalize(input.assertions());
        FixtureCase fixture = new FixtureCase(null, 0, input.caseCode(), input.caseName(), input.caseOrder(), executionMode, input.direction(),
                source, expected, input.expectedSuccess(), blankToNull(input.expectedDiagnosticCode()),
                assertionDocument == null ? null : canonicalJson.canonicalString(assertionDocument), null);
        if (!codes.add(fixture.caseCode())) throw new IllegalArgumentException("FixtureCase code is duplicated: " + fixture.caseCode());
        return fixture;
    }

    private ArrayNode content(List<FixtureCase> cases) {
        ArrayNode array = json.createArrayNode();
        for (FixtureCase fixture : cases) {
            ObjectNode node = json.createObjectNode();
            node.put("caseCode", fixture.caseCode()); node.put("caseName", fixture.caseName());
            node.put("caseOrder", fixture.caseOrder()); node.put("executionMode", fixture.executionMode().name());
            node.put("direction", fixture.direction().name());
            node.set("source", read(fixture.sourceDocument()));
            if (fixture.expectedDocument() != null) node.set("expected", read(fixture.expectedDocument()));
            node.put("expectedSuccess", fixture.expectedSuccess());
            if (fixture.expectedDiagnosticCode() != null) node.put("expectedDiagnosticCode", fixture.expectedDiagnosticCode());
            if (fixture.assertionDocument() != null) node.set("assertions", read(fixture.assertionDocument()));
            array.add(canonicalJson.canonicalNode(node));
        }
        return array;
    }

    private JsonNode read(String value) { try { return json.readTree(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static JsonNode requiredObject(JsonNode value, String field) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException(field + " must be a JSON object");
        return value;
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) throw new IllegalArgumentException("X-Operator is invalid");
        return value.trim();
    }

    public record FixtureCaseInput(String caseCode, String caseName, int caseOrder,
            FixtureExecutionMode executionMode, MappingAssetDirection direction, JsonNode source, JsonNode expected,
            boolean expectedSuccess, String expectedDiagnosticCode, JsonNode assertions) {}
}
