package com.ftk.tpip.control.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.catalog.domain.repository.CanonicalOperationRepository;
import com.ftk.tpip.consumer.domain.model.*;
import com.ftk.tpip.consumer.domain.repository.ConsumerAccessRepository;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.shared.AssetCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumerAccessApplicationService {
    private final ConsumerAccessRepository repository;
    private final CanonicalOperationRepository operations;
    private final CanonicalJsonService canonical;
    private final ObjectMapper json;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ConsumerAccessApplicationService(ConsumerAccessRepository repository,
            CanonicalOperationRepository operations, CanonicalJsonService canonical, ObjectMapper json) {
        this(repository, operations, canonical, json, Clock.systemUTC());
    }
    ConsumerAccessApplicationService(ConsumerAccessRepository repository, CanonicalOperationRepository operations,
            CanonicalJsonService canonical, ObjectMapper json, Clock clock) {
        this.repository=repository; this.operations=operations; this.canonical=canonical; this.json=json; this.clock=clock;
    }

    @Transactional(readOnly=true) public List<ConsumerProject> projects() { return repository.findProjects(); }
    @Transactional public ConsumerProject createProject(String code,String name,String owner,String description,String actor) {
        return repository.createProject(ConsumerProject.create(AssetCode.of(code),name,owner,description),actor(actor));
    }
    @Transactional(readOnly=true) public List<ConsumerApplication> applications(Long projectId) { return repository.findApplications(projectId); }
    @Transactional public ConsumerApplication createApplication(long projectId,String code,String name,String owner,String description,String actor) {
        ConsumerProject project=repository.findProject(projectId).orElseThrow(()->new IllegalArgumentException("调用方项目不存在"));
        if(project.status()!=ConsumerStatus.ACTIVE)throw new IllegalArgumentException("调用方项目未启用");
        return repository.createApplication(ConsumerApplication.create(projectId,AssetCode.of(code),name,owner,description),actor(actor));
    }
    @Transactional(readOnly=true) public ApplicationDetail application(long id) {
        ConsumerApplication app=requiredApplication(id);
        List<GrantView> grants=repository.findGrants(id).stream().map(grant->new GrantView(grant,
                operations.findById(grant.operationId()).map(value->value.operationCode().value()).orElse("unknown"),
                operations.findById(grant.operationId()).map(value->value.operationName()).orElse("服务不存在"),
                repository.findGrantVersions(grant.id()))).toList();
        return new ApplicationDetail(app,repository.findCredentials(id),grants);
    }
    @Transactional public ConsumerCredentialVersion createCredential(long appId,String secretReference,
            Instant validFrom,Instant validUntil,String actor) {
        requiredApplication(appId);
        String appKey="tpip_"+HexFormat.of().formatHex(random.generateSeed(16));
        Instant from=validFrom==null?clock.instant():validFrom;
        String checksum=canonical.sha256(appId+"|"+appKey+"|"+secretReference+"|"+from+"|"+validUntil+"|HMAC_SHA256");
        return repository.createCredential(ConsumerCredentialVersion.draft(appId,appKey,secretReference,from,validUntil,checksum),actor(actor));
    }
    @Transactional public ConsumerCredentialVersion publishCredential(long appId,long versionId,String actor) {
        requiredApplication(appId); return repository.publishCredential(appId,versionId,actor(actor));
    }
    @Transactional public ConsumerCredentialVersion revokeCredential(long appId,long versionId,String actor) {
        requiredApplication(appId); return repository.revokeCredential(appId,versionId,actor(actor));
    }
    @Transactional public GrantView createGrant(long appId,long operationId,String owner,Instant validFrom,
            Instant validUntil,Integer qps,Integer burst,Long quota,List<String> cidrs,Set<String> scenarios,String actor) {
        ConsumerApplication app=requiredApplication(appId);
        var operation=operations.findById(operationId).orElseThrow(()->new IllegalArgumentException("接入服务不存在"));
        String user=actor(actor);
        ConsumerServiceGrant grant=repository.createGrant(ConsumerServiceGrant.create(appId,operationId,
                AssetCode.of(app.appCode().value()+".grant."+operation.operationCode().value()),owner),user);
        ConsumerServiceGrantVersion version=createGrantVersion(grant.id(),validFrom,validUntil,qps,burst,quota,cidrs,scenarios,user);
        return new GrantView(grant,operation.operationCode().value(),operation.operationName(),List.of(version));
    }
    @Transactional public ConsumerServiceGrantVersion createGrantVersion(long grantId,Instant validFrom,
            Instant validUntil,Integer qps,Integer burst,Long quota,List<String> cidrs,Set<String> scenarios,String actor) {
        repository.findGrant(grantId).orElseThrow(()->new IllegalArgumentException("服务授权不存在"));
        Instant from=validFrom==null?clock.instant():validFrom;
        var content=json.createObjectNode().put("grantId",grantId).put("validFrom",from.toString());
        if(validUntil!=null)content.put("validUntil",validUntil.toString());
        if(qps!=null)content.put("qpsLimit",qps); if(burst!=null)content.put("burstLimit",burst); if(quota!=null)content.put("dailyQuota",quota);
        content.set("allowedCidrs",json.valueToTree(cidrs==null?List.of():cidrs));
        content.set("allowedScenarios",json.valueToTree(scenarios==null?Set.of():scenarios));
        String checksum=canonical.sha256(canonical.canonicalString(content));
        return repository.createGrantVersion(ConsumerServiceGrantVersion.draft(grantId,from,validUntil,qps,burst,quota,
                cidrs,scenarios,"{}","{}",checksum),actor(actor));
    }
    @Transactional public ConsumerServiceGrantVersion publishGrantVersion(long grantId,long versionId,String actor) {
        return repository.publishGrantVersion(grantId,versionId,actor(actor));
    }
    @Transactional(readOnly=true) public PublishedSnapshot publishedSnapshot() {
        Instant generatedAt=clock.instant(); List<ConsumerAccessRepository.PublishedAccess> entries=repository.findPublishedAccess(generatedAt);
        String checksum=canonical.sha256(canonical.canonicalString(json.valueToTree(entries)));
        return new PublishedSnapshot("tpip.consumer-access/v1",generatedAt,checksum,entries);
    }
    @Transactional public void recordAudit(InvocationAudit command) {
        repository.recordInvocation(command.requestId(),command.applicationId(),command.appKey(),command.serviceCode(),
                command.grantId(),command.grantVersionId(),command.result(),command.rejectReason(),command.durationMs());
    }
    private ConsumerApplication requiredApplication(long id){ConsumerApplication value=repository.findApplication(id).orElseThrow(()->new IllegalArgumentException("调用方应用不存在"));if(value.status()!=ConsumerStatus.ACTIVE)throw new IllegalArgumentException("调用方应用未启用");return value;}
    private static String actor(String value){if(value==null||value.isBlank()||value.trim().length()>100)throw new IllegalArgumentException("X-Operator不合法");return value.trim();}

    public record ApplicationDetail(ConsumerApplication application,List<ConsumerCredentialVersion> credentials,List<GrantView> grants){}
    public record GrantView(ConsumerServiceGrant grant,String serviceCode,String serviceName,List<ConsumerServiceGrantVersion> versions){}
    public record PublishedSnapshot(String apiVersion,Instant generatedAt,String checksum,List<ConsumerAccessRepository.PublishedAccess> entries){}
    public record InvocationAudit(String requestId,Long applicationId,String appKey,String serviceCode,Long grantId,
            Long grantVersionId,String result,String rejectReason,long durationMs){}
}
