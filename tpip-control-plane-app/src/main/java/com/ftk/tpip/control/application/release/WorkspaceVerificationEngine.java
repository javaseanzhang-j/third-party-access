package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.integration.BindingVersionBundlePreviewApplicationService;
import com.ftk.tpip.control.application.integration.IntegrationMappingApplicationService;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.control.application.provider.ProviderEndpointProbeApplicationService;
import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.integration.domain.repository.*;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.FixtureSuiteRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceVerificationEngine implements WorkspaceVerificationExecutor {
    private final IntegrationBindingRepository bindings;
    private final IntegrationBindingVersionRepository bindingVersions;
    private final IntegrationMappingRepository mappings;
    private final FixtureSuiteRepository suites;
    private final BindingVersionBundlePreviewApplicationService previews;
    private final ProviderEndpointProbeApplicationService endpointProbes;
    private final IntegrationMappingApplicationService mappingService;
    private final CanonicalJsonService canonicalJson;
    private final ObjectMapper json;
    private final FixtureAssertionEvaluator assertions;
    private final WorkspaceRemoteCallExecutor remoteCalls;
    private final Clock clock;

    @Autowired
    public WorkspaceVerificationEngine(IntegrationBindingRepository bindings,
            IntegrationBindingVersionRepository bindingVersions, IntegrationMappingRepository mappings,
            FixtureSuiteRepository suites, BindingVersionBundlePreviewApplicationService previews,
            ProviderEndpointProbeApplicationService endpointProbes, IntegrationMappingApplicationService mappingService,
            CanonicalJsonService canonicalJson, ObjectMapper json, FixtureAssertionEvaluator assertions,
            WorkspaceRemoteCallExecutor remoteCalls) {
        this(bindings, bindingVersions, mappings, suites, previews, endpointProbes, mappingService,
                canonicalJson, json, assertions, remoteCalls, Clock.systemUTC());
    }

    WorkspaceVerificationEngine(IntegrationBindingRepository bindings,
            IntegrationBindingVersionRepository bindingVersions, IntegrationMappingRepository mappings,
            FixtureSuiteRepository suites, BindingVersionBundlePreviewApplicationService previews,
            ProviderEndpointProbeApplicationService endpointProbes, IntegrationMappingApplicationService mappingService,
            CanonicalJsonService canonicalJson, ObjectMapper json, FixtureAssertionEvaluator assertions,
            WorkspaceRemoteCallExecutor remoteCalls, Clock clock) {
        this.bindings = bindings; this.bindingVersions = bindingVersions; this.mappings = mappings;
        this.suites = suites; this.previews = previews; this.endpointProbes = endpointProbes;
        this.mappingService = mappingService; this.canonicalJson = canonicalJson; this.json = json;
        this.assertions = assertions; this.remoteCalls = remoteCalls; this.clock = clock;
    }

    @Override
    public List<CheckOutcome> execute(long bindingId, long bindingVersionId, long fixtureSuiteVersionId,
            long runId, String actor) {
        var outcomes = new ArrayList<CheckOutcome>();
        IntegrationBinding binding = bindings.findById(bindingId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace binding does not exist"));
        IntegrationBindingVersion bindingVersion = bindingVersions.findVersion(bindingId, bindingVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace BindingVersion does not exist"));
        boolean published = bindingVersion.lifecycleStatus() == BindingVersionLifecycleStatus.PUBLISHED;
        outcomes.add(outcome("BINDING_DEPENDENCY_CLOSURE", "Binding dependency closure", published,
                details("bindingVersionId", bindingVersionId), details("lifecycleStatus", bindingVersion.lifecycleStatus().name())));
        if (!published) return outcomes;

        FixtureSuiteVersion suiteVersion = suites.findVersionById(fixtureSuiteVersionId)
                .orElseThrow(() -> new IllegalArgumentException("FixtureSuiteVersion does not exist"));
        FixtureSuite suite = suites.findById(suiteVersion.suiteId()).orElseThrow();
        boolean fixtureEligible = suite.bindingId() == bindingId && suite.status() == FixtureSuiteStatus.ACTIVE
                && suiteVersion.lifecycleStatus() == FixtureSuiteVersionStatus.PUBLISHED;
        outcomes.add(outcome("FIXTURE_SUITE_ELIGIBLE", "Published FixtureSuite binding", fixtureEligible,
                details("fixtureSuiteVersionId", fixtureSuiteVersionId), details("suiteCode", suite.suiteCode().value())));
        if (!fixtureEligible) return outcomes;

        try {
            var manifest = previews.preview(bindingId, bindingVersionId, "verification.bundle." + runId, "0.0.0");
            ObjectNode evidence = json.createObjectNode();
            evidence.put("manifestChecksum", manifest.checksum());
            evidence.put("mappingPlanCount", manifest.mappingPlans().size());
            evidence.put("secretReferenceCount", manifest.secretReferences().size());
            if(manifest.serviceRoutePlan()!=null){
                evidence.put("routeVersionId",manifest.serviceRoutePlan().routeVersionId());
                evidence.put("routeContentChecksum",manifest.serviceRoutePlan().contentChecksum());
                evidence.put("routeTargetCount",manifest.serviceRoutePlan().targets().size());
            }
            outcomes.add(outcome("BUNDLE_PREVIEW", "Bundle preview compilation", true,
                    details("runtimeCompatibility", manifest.runtimeCompatibility()), evidence));
        } catch (RuntimeException failure) {
            outcomes.add(failed("BUNDLE_PREVIEW", "Bundle preview compilation", failure));
        }

        try {
            var probe = endpointProbes.probe(bindingVersion.endpointId(), actor);
            outcomes.add(outcome("ENDPOINT_PROBE", "Published endpoint connectivity", "SUCCESS".equals(probe.outcome()),
                    details("reasonCode", probe.reasonCode()), details("latencyMs", probe.latencyMs())));
        } catch (RuntimeException failure) {
            outcomes.add(failed("ENDPOINT_PROBE", "Published endpoint connectivity", failure));
        }

        remoteCalls.validateFixtureCount(suiteVersion.cases().stream()
                .filter(fixture -> fixture.executionMode() == FixtureExecutionMode.REMOTE_CALL).count());
        for (FixtureCase fixture : suiteVersion.cases()) {
            outcomes.add(fixture.executionMode() == FixtureExecutionMode.REMOTE_CALL
                    ? executeRemoteFixture(binding, bindingVersion, fixture, runId)
                    : executeMappingFixture(binding, bindingVersion, fixture, runId));
        }
        return List.copyOf(outcomes);
    }

    private CheckOutcome executeMappingFixture(IntegrationBinding binding, IntegrationBindingVersion version,
            FixtureCase fixture, long runId) {
        Instant started = clock.instant();
        try {
            Long mappingVersionId = switch (fixture.direction()) {
                case OUTBOUND_REQUEST -> version.requestMappingVersionId();
                case INBOUND_RESPONSE -> version.responseMappingVersionId();
                default -> null;
            };
            if (mappingVersionId == null) throw new IllegalArgumentException("BindingVersion has no mapping for " + fixture.direction());
            IntegrationMappingVersion mappingVersion = mappings.findVersionById(mappingVersionId).orElseThrow();
            IntegrationMapping mapping = mappings.findById(mappingVersion.mappingId()).orElseThrow();
            var result = mappingService.test(mapping.id(), mappingVersion.id(), read(fixture.sourceDocument()),
                    "verification-" + runId + "-" + fixture.caseCode(), "verification-" + runId,
                    binding.bindingCode().value(), java.util.Map.of("verificationRunId", runId));
            FixtureAssertionEvaluator.EvaluationReport assertionReport = null;
            boolean matched;
            if (fixture.assertionDocument() != null) {
                var diagnosticCodes = result.diagnostics().stream().map(d -> d.code()).collect(java.util.stream.Collectors.toSet());
                assertionReport = assertions.evaluate(read(fixture.assertionDocument()),
                        FixtureAssertionEvaluator.AssertionContext.mapping(result.successful(), result.output(), diagnosticCodes));
                matched = assertionReport.passed();
            } else if (fixture.expectedSuccess()) {
                matched = result.successful() && canonicalJson.canonicalNode(result.output())
                        .equals(canonicalJson.canonicalNode(read(fixture.expectedDocument())));
            } else {
                matched = !result.successful() && (fixture.expectedDiagnosticCode() == null
                        || result.diagnostics().stream().anyMatch(d -> fixture.expectedDiagnosticCode().equals(d.code())));
            }
            ObjectNode details = json.createObjectNode();
            details.put("expectedSuccess", fixture.expectedSuccess()); details.put("actualSuccess", result.successful());
            ObjectNode evidence = json.createObjectNode();
            evidence.put("mappingVersionId", mappingVersion.id()); evidence.put("compiledPlanChecksum", result.compiledPlanChecksum());
            if (result.output() != null) evidence.set("output", result.output());
            evidence.set("diagnostics", json.valueToTree(result.diagnostics()));
            if (assertionReport != null) {
                details.put("assertionCount", assertionReport.results().size());
                details.put("failedAssertionCount", assertionReport.results().stream().filter(item -> !item.passed()).count());
                evidence.set("assertions", json.valueToTree(assertionReport.results()));
            }
            return new CheckOutcome("FIXTURE." + fixture.caseCode(), fixture.caseName(),
                    matched ? VerificationCheckStatus.PASSED : VerificationCheckStatus.FAILED,
                    canonicalJson.write(details), canonicalJson.write(evidence), started, clock.instant());
        } catch (RuntimeException failure) {
            return failed("FIXTURE." + fixture.caseCode(), fixture.caseName(), failure, started);
        }
    }

    private CheckOutcome executeRemoteFixture(IntegrationBinding binding, IntegrationBindingVersion version,
            FixtureCase fixture, long runId) {
        Instant started = clock.instant();
        try {
            var remote = remoteCalls.execute(binding, version, fixture, runId);
            if (remote.provider() == null) {
                throw new IllegalStateException("REMOTE_CALL did not reach provider: " + remote.invocation().result().code());
            }
            var provider = remote.provider();
            var diagnosticCodes = java.util.Set.of(remote.invocation().result().code());
            var context = new FixtureAssertionEvaluator.AssertionContext(remote.invocation().result().success(),
                    provider.body(), diagnosticCodes, provider.statusCode(), provider.headers());
            var report = assertions.evaluate(read(fixture.assertionDocument()), context);
            ObjectNode details = json.createObjectNode();
            details.put("executionMode", FixtureExecutionMode.REMOTE_CALL.name());
            details.put("runtimeSuccess", remote.invocation().result().success());
            details.put("runtimeResultCode", remote.invocation().result().code());
            details.put("assertionCount", report.results().size());
            details.put("failedAssertionCount", report.results().stream().filter(item -> !item.passed()).count());
            ObjectNode evidence = json.createObjectNode();
            evidence.put("httpStatus", provider.statusCode());
            evidence.put("durationMs", provider.duration().toMillis());
            evidence.put("requestBytes", remote.requestBytes());
            evidence.put("responseBytes", json.writeValueAsBytes(provider.body()).length);
            evidence.put("responseBodyChecksum", canonicalJson.sha256(canonicalJson.canonicalString(provider.body())));
            evidence.set("responseHeaderNames", json.valueToTree(provider.headers().keySet().stream().sorted().toList()));
            evidence.set("assertions", redactedRemoteAssertions(report));
            return new CheckOutcome("FIXTURE." + fixture.caseCode(), fixture.caseName(),
                    report.passed() ? VerificationCheckStatus.PASSED : VerificationCheckStatus.FAILED,
                    canonicalJson.write(details), canonicalJson.write(evidence), started, clock.instant());
        } catch (RuntimeException failure) {
            return failed("FIXTURE." + fixture.caseCode(), fixture.caseName(), failure, started);
        } catch (Exception failure) {
            return failed("FIXTURE." + fixture.caseCode(), fixture.caseName(),
                    new IllegalStateException("REMOTE_CALL evidence serialization failed", failure), started);
        }
    }

    private com.fasterxml.jackson.databind.node.ArrayNode redactedRemoteAssertions(
            FixtureAssertionEvaluator.EvaluationReport report) {
        var result = json.createArrayNode();
        for (var assertion : report.results()) {
            ObjectNode node = json.createObjectNode();
            node.put("code", assertion.code()); node.put("type", assertion.type().name());
            node.put("passed", assertion.passed()); node.put("message", assertion.message());
            if (assertion.type() == FixtureAssertionEvaluator.AssertionType.SUCCESS
                    || assertion.type() == FixtureAssertionEvaluator.AssertionType.HTTP_STATUS
                    || assertion.type() == FixtureAssertionEvaluator.AssertionType.DIAGNOSTIC_CODE
                    || assertion.type() == FixtureAssertionEvaluator.AssertionType.JSON_SCHEMA) {
                node.set("actual", assertion.actual());
            } else if (assertion.actual().isMissingNode()) {
                node.put("actualMissing", true);
            } else {
                node.put("actualRedacted", true);
                node.put("actualChecksum", canonicalJson.sha256(canonicalJson.canonicalString(assertion.actual())));
            }
            result.add(node);
        }
        return result;
    }

    private CheckOutcome outcome(String code, String name, boolean passed, ObjectNode details, ObjectNode evidence) {
        Instant now = clock.instant();
        return new CheckOutcome(code, name, passed ? VerificationCheckStatus.PASSED : VerificationCheckStatus.FAILED,
                canonicalJson.write(details), canonicalJson.write(evidence), now, clock.instant());
    }
    private CheckOutcome failed(String code, String name, RuntimeException failure) { return failed(code, name, failure, clock.instant()); }
    private CheckOutcome failed(String code, String name, RuntimeException failure, Instant started) {
        ObjectNode details = json.createObjectNode(); details.put("error", safe(failure));
        return new CheckOutcome(code, name, VerificationCheckStatus.FAILED, canonicalJson.write(details), "{}", started, clock.instant());
    }
    private ObjectNode details(String key, String value) { ObjectNode node=json.createObjectNode();node.put(key,value);return node; }
    private ObjectNode details(String key, long value) { ObjectNode node=json.createObjectNode();node.put(key,value);return node; }
    private JsonNode read(String value) { try { return json.readTree(value); } catch (Exception e) { throw new IllegalStateException("Stored Fixture JSON is invalid", e); } }
    private static String safe(RuntimeException failure) { return failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage(); }

    public record CheckOutcome(String code, String name, VerificationCheckStatus status,
            String details, String evidence, Instant startedAt, Instant finishedAt) {}
}
