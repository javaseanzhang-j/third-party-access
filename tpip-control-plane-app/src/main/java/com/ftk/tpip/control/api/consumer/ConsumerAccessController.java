package com.ftk.tpip.control.api.consumer;

import com.ftk.tpip.consumer.domain.model.*;
import com.ftk.tpip.control.application.consumer.ConsumerAccessApplicationService;
import com.ftk.tpip.control.application.consumer.ConsumerAccessApplicationService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/consumer-access")
public class ConsumerAccessController {
    private final ConsumerAccessApplicationService service;
    public ConsumerAccessController(ConsumerAccessApplicationService service){this.service=service;}

    @GetMapping("/projects") public List<ConsumerProject> projects(){return service.projects();}
    @PostMapping("/projects") public ResponseEntity<ConsumerProject> createProject(@Valid @RequestBody CreateProject request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor){
        ConsumerProject value=service.createProject(request.projectCode(),request.projectName(),request.ownerCode(),request.description(),actor);
        return ResponseEntity.created(URI.create("/control/v1/consumer-access/projects/"+value.id())).body(value);
    }
    @GetMapping("/applications") public List<ConsumerApplication> applications(@RequestParam(required=false) Long projectId){return service.applications(projectId);}
    @PostMapping("/projects/{projectId}/applications") public ResponseEntity<ConsumerApplication> createApplication(
            @PathVariable @Positive long projectId,@Valid @RequestBody CreateApplication request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor){
        ConsumerApplication value=service.createApplication(projectId,request.appCode(),request.appName(),request.ownerCode(),request.description(),actor);
        return ResponseEntity.created(URI.create("/control/v1/consumer-access/applications/"+value.id())).body(value);
    }
    @GetMapping("/applications/{appId}") public ApplicationDetail application(@PathVariable @Positive long appId){return service.application(appId);}
    @PostMapping("/applications/{appId}/credential-versions") public ResponseEntity<ConsumerCredentialVersion> createCredential(
            @PathVariable @Positive long appId,@Valid @RequestBody CreateCredential request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor){
        ConsumerCredentialVersion value=service.createCredential(appId,request.secretReference(),request.validFrom(),request.validUntil(),actor);
        return ResponseEntity.created(URI.create("/control/v1/consumer-access/applications/"+appId+"/credential-versions/"+value.id())).body(value);
    }
    @PostMapping("/applications/{appId}/credential-versions/{versionId}:publish") public ConsumerCredentialVersion publishCredential(
            @PathVariable @Positive long appId,@PathVariable @Positive long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor){return service.publishCredential(appId,versionId,actor);}
    @PostMapping("/applications/{appId}/credential-versions/{versionId}:revoke") public ConsumerCredentialVersion revokeCredential(
            @PathVariable @Positive long appId,@PathVariable @Positive long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor){return service.revokeCredential(appId,versionId,actor);}
    @PostMapping("/applications/{appId}/grants") public ResponseEntity<GrantView> createGrant(@PathVariable @Positive long appId,
            @Valid @RequestBody CreateGrant request,@RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor){
        GrantView value=service.createGrant(appId,request.operationId(),request.ownerCode(),request.validFrom(),request.validUntil(),
                request.qpsLimit(),request.burstLimit(),request.dailyQuota(),request.allowedCidrs(),request.allowedScenarios(),actor);
        return ResponseEntity.created(URI.create("/control/v1/consumer-access/grants/"+value.grant().id())).body(value);
    }
    @PostMapping("/grants/{grantId}/versions") public ResponseEntity<ConsumerServiceGrantVersion> createGrantVersion(
            @PathVariable @Positive long grantId,@Valid @RequestBody GrantVersionRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor){
        ConsumerServiceGrantVersion value=service.createGrantVersion(grantId,request.validFrom(),request.validUntil(),request.qpsLimit(),
                request.burstLimit(),request.dailyQuota(),request.allowedCidrs(),request.allowedScenarios(),actor);
        return ResponseEntity.created(URI.create("/control/v1/consumer-access/grants/"+grantId+"/versions/"+value.id())).body(value);
    }
    @PostMapping("/grants/{grantId}/versions/{versionId}:publish") public ConsumerServiceGrantVersion publishGrant(
            @PathVariable @Positive long grantId,@PathVariable @Positive long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor){return service.publishGrantVersion(grantId,versionId,actor);}
    @GetMapping("/runtime-snapshot") public PublishedSnapshot runtimeSnapshot(){return service.publishedSnapshot();}
    @PostMapping("/invocation-audits") public ResponseEntity<Void> invocationAudit(@Valid @RequestBody InvocationAuditRequest request){
        service.recordAudit(new InvocationAudit(request.requestId(),request.applicationId(),request.appKey(),request.serviceCode(),
                request.grantId(),request.grantVersionId(),request.result(),request.rejectReason(),request.durationMs()));
        return ResponseEntity.accepted().build();
    }

    public record CreateProject(@NotBlank @Size(max=180) String projectCode,@NotBlank @Size(max=200) String projectName,
            @NotBlank @Size(max=100) String ownerCode,@Size(max=1000) String description){}
    public record CreateApplication(@NotBlank @Size(max=180) String appCode,@NotBlank @Size(max=200) String appName,
            @NotBlank @Size(max=100) String ownerCode,@Size(max=1000) String description){}
    public record CreateCredential(@NotBlank @Size(max=500) @Pattern(regexp="^env://TPIP_SECRET_[A-Z0-9_]{1,100}$") String secretReference,
            Instant validFrom,Instant validUntil){}
    public record CreateGrant(@Positive long operationId,@NotBlank @Size(max=100) String ownerCode,Instant validFrom,
            Instant validUntil,@Positive Integer qpsLimit,@Positive Integer burstLimit,@Positive Long dailyQuota,
            @Size(max=100) List<@Size(max=64) String> allowedCidrs,@Size(max=100) Set<@Pattern(regexp="^[a-zA-Z0-9._-]{1,100}$") String> allowedScenarios){}
    public record GrantVersionRequest(Instant validFrom,Instant validUntil,@Positive Integer qpsLimit,
            @Positive Integer burstLimit,@Positive Long dailyQuota,@Size(max=100) List<@Size(max=64) String> allowedCidrs,
            @Size(max=100) Set<@Pattern(regexp="^[a-zA-Z0-9._-]{1,100}$") String> allowedScenarios){}
    public record InvocationAuditRequest(@NotBlank @Size(max=128) String requestId,Long applicationId,@Size(max=80) String appKey,
            @NotBlank @Size(max=180) String serviceCode,Long grantId,Long grantVersionId,
            @NotBlank @Pattern(regexp="^(ALLOWED|DENIED|ERROR)$") String result,@Size(max=64) String rejectReason,
            @PositiveOrZero long durationMs){}
}
