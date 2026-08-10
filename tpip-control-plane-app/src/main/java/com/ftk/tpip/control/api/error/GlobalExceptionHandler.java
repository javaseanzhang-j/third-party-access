package com.ftk.tpip.control.api.error;

import com.ftk.tpip.catalog.domain.exception.OperationCodeAlreadyExistsException;
import com.ftk.tpip.catalog.domain.exception.CatalogCodeAlreadyExistsException;
import com.ftk.tpip.catalog.domain.exception.CatalogConcurrentModificationException;
import com.ftk.tpip.catalog.domain.exception.CanonicalContractCodeAlreadyExistsException;
import com.ftk.tpip.catalog.domain.exception.CanonicalContractConcurrentModificationException;
import com.ftk.tpip.catalog.domain.exception.CanonicalContractVersionAlreadyExistsException;
import com.ftk.tpip.catalog.domain.exception.CanonicalContractVersionLifecycleException;
import com.ftk.tpip.catalog.domain.exception.OperationConcurrentModificationException;
import com.ftk.tpip.control.application.catalog.CanonicalOperationNotFoundException;
import com.ftk.tpip.control.application.catalog.CatalogAssetNotFoundException;
import com.ftk.tpip.control.application.catalog.CanonicalContractNotFoundException;
import com.ftk.tpip.control.application.catalog.CanonicalContractVersionNotFoundException;
import com.ftk.tpip.control.application.integration.BindingReferenceException;
import com.ftk.tpip.control.application.integration.IntegrationBindingNotFoundException;
import com.ftk.tpip.control.application.integration.IntegrationBindingVersionNotFoundException;
import com.ftk.tpip.control.application.integration.IntegrationMappingNotFoundException;
import com.ftk.tpip.control.application.integration.IntegrationMappingVersionNotFoundException;
import com.ftk.tpip.control.application.provider.CredentialRefNotFoundException;
import com.ftk.tpip.control.application.provider.ProviderNotFoundException;
import com.ftk.tpip.control.application.provider.ProviderContractNotFoundException;
import com.ftk.tpip.control.application.provider.ProviderContractVersionNotFoundException;
import com.ftk.tpip.control.application.provider.EndpointNotFoundException;
import com.ftk.tpip.provider.domain.exception.CredentialRefAlreadyExistsException;
import com.ftk.tpip.provider.domain.exception.CredentialRefConcurrentModificationException;
import com.ftk.tpip.integration.domain.exception.BindingCodeAlreadyExistsException;
import com.ftk.tpip.integration.domain.exception.BindingConcurrentModificationException;
import com.ftk.tpip.integration.domain.exception.BindingVersionLifecycleException;
import com.ftk.tpip.integration.domain.exception.MappingCodeAlreadyExistsException;
import com.ftk.tpip.integration.domain.exception.MappingDirectionAlreadyExistsException;
import com.ftk.tpip.integration.domain.exception.MappingConcurrentModificationException;
import com.ftk.tpip.integration.domain.exception.MappingVersionLifecycleException;
import com.ftk.tpip.mapping.api.MappingCompilationException;
import com.ftk.tpip.policy.api.PolicyCompilationException;
import com.ftk.tpip.control.application.integration.PolicyTypeNotFoundException;
import com.ftk.tpip.control.application.integration.IntegrationPolicyNotFoundException;
import com.ftk.tpip.control.application.integration.IntegrationPolicyVersionNotFoundException;
import com.ftk.tpip.integration.domain.exception.PolicyTypeAlreadyExistsException;
import com.ftk.tpip.integration.domain.exception.PolicyCodeAlreadyExistsException;
import com.ftk.tpip.integration.domain.exception.PolicyConcurrentModificationException;
import com.ftk.tpip.integration.domain.exception.PolicyVersionLifecycleException;
import com.ftk.tpip.provider.domain.exception.ContractVersionLifecycleException;
import com.ftk.tpip.provider.domain.exception.EndpointCodeOwnershipException;
import com.ftk.tpip.provider.domain.exception.EndpointCredentialMismatchException;
import com.ftk.tpip.provider.domain.exception.EndpointLifecycleException;
import com.ftk.tpip.provider.domain.exception.ProviderCodeAlreadyExistsException;
import com.ftk.tpip.provider.domain.exception.ProviderConcurrentModificationException;
import com.ftk.tpip.provider.domain.exception.ProviderContractCodeAlreadyExistsException;
import com.ftk.tpip.provider.domain.exception.ProviderContractConcurrentModificationException;
import com.ftk.tpip.provider.domain.exception.ProviderContractVersionAlreadyExistsException;
import com.ftk.tpip.control.application.release.WorkspaceNotFoundException;
import com.ftk.tpip.control.application.release.BundleNotFoundException;
import com.ftk.tpip.control.application.deployment.ActiveRouteNotFoundException;
import com.ftk.tpip.control.application.deployment.DeploymentNotFoundException;
import com.ftk.tpip.control.application.deployment.HealthAutomationUnauthorizedException;
import com.ftk.tpip.control.application.deployment.DeploymentHealthAlertNotFoundException;
import com.ftk.tpip.control.application.notification.NotificationOutboxNotFoundException;
import com.ftk.tpip.release.domain.exception.DeploymentAlreadyExistsException;
import com.ftk.tpip.release.domain.exception.DeploymentConcurrentModificationException;
import com.ftk.tpip.release.domain.exception.DeploymentLifecycleException;
import com.ftk.tpip.release.domain.exception.*;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HealthAutomationUnauthorizedException.class)
    ResponseEntity<ApiErrorResponse> handleHealthAutomationUnauthorized(
            HealthAutomationUnauthorizedException exception) {
        return error(HttpStatus.UNAUTHORIZED, "TPIP_HEALTH_AUTOMATION_UNAUTHORIZED",
                exception.getMessage(), Map.of());
    }

    @ExceptionHandler({WorkspaceNotFoundException.class, BundleNotFoundException.class,
            DeploymentNotFoundException.class, ActiveRouteNotFoundException.class,
            DeploymentHealthAlertNotFoundException.class, NotificationOutboxNotFoundException.class})
    ResponseEntity<ApiErrorResponse> handleReleaseNotFound(RuntimeException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_RELEASE_ASSET_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler({WorkspaceAlreadyExistsException.class, BundleAlreadyExistsException.class,
            DeploymentAlreadyExistsException.class})
    ResponseEntity<ApiErrorResponse> handleReleaseDuplicate(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_RELEASE_ASSET_ALREADY_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler({WorkspaceLifecycleException.class, BundleLifecycleException.class,
            DeploymentLifecycleException.class})
    ResponseEntity<ApiErrorResponse> handleReleaseLifecycle(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_RELEASE_LIFECYCLE_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler({WorkspaceConcurrentModificationException.class, DeploymentConcurrentModificationException.class})
    ResponseEntity<ApiErrorResponse> handleWorkspaceConcurrent(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_OPTIMISTIC_LOCK_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(GlobalImpactJobCommandConflictException.class)
    ResponseEntity<ApiErrorResponse> handleGlobalImpactJobCommandConflict(
            GlobalImpactJobCommandConflictException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_GLOBAL_IMPACT_JOB_COMMAND_CONFLICT",
                exception.getMessage(), Map.of("refreshRequired", true));
    }

    @ExceptionHandler(CanonicalContractNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleCanonicalContractNotFound(CanonicalContractNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_CANONICAL_CONTRACT_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CanonicalContractVersionNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleCanonicalContractVersionNotFound(CanonicalContractVersionNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_CANONICAL_CONTRACT_VERSION_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CanonicalContractCodeAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleCanonicalContractDuplicate(CanonicalContractCodeAlreadyExistsException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_CANONICAL_CONTRACT_ALREADY_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CanonicalContractVersionAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleCanonicalContractVersionDuplicate(CanonicalContractVersionAlreadyExistsException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_CANONICAL_CONTRACT_VERSION_ALREADY_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CanonicalContractConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> handleCanonicalContractConcurrent(CanonicalContractConcurrentModificationException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_OPTIMISTIC_LOCK_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CanonicalContractVersionLifecycleException.class)
    ResponseEntity<ApiErrorResponse> handleCanonicalContractLifecycle(CanonicalContractVersionLifecycleException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_CANONICAL_CONTRACT_VERSION_LIFECYCLE_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CatalogAssetNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleCatalogNotFound(CatalogAssetNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_CATALOG_ASSET_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CatalogCodeAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleCatalogDuplicate(CatalogCodeAlreadyExistsException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_CATALOG_ASSET_ALREADY_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CatalogConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> handleCatalogConcurrent(CatalogConcurrentModificationException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_OPTIMISTIC_LOCK_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CanonicalOperationNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleOperationNotFound(CanonicalOperationNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_OPERATION_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(IntegrationBindingNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleBindingNotFound(IntegrationBindingNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_BINDING_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(IntegrationBindingVersionNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleBindingVersionNotFound(IntegrationBindingVersionNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_BINDING_VERSION_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(BindingVersionLifecycleException.class)
    ResponseEntity<ApiErrorResponse> handleBindingVersionLifecycle(BindingVersionLifecycleException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_BINDING_VERSION_LIFECYCLE_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(IntegrationMappingNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleMappingNotFound(IntegrationMappingNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_MAPPING_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(IntegrationMappingVersionNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleMappingVersionNotFound(IntegrationMappingVersionNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_MAPPING_VERSION_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler({MappingCodeAlreadyExistsException.class, MappingDirectionAlreadyExistsException.class})
    ResponseEntity<ApiErrorResponse> handleMappingDuplicate(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_MAPPING_ALREADY_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MappingConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> handleMappingConcurrent(MappingConcurrentModificationException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_OPTIMISTIC_LOCK_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MappingVersionLifecycleException.class)
    ResponseEntity<ApiErrorResponse> handleMappingLifecycle(MappingVersionLifecycleException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_MAPPING_VERSION_LIFECYCLE_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MappingCompilationException.class)
    ResponseEntity<ApiErrorResponse> handleMappingCompilation(MappingCompilationException exception) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "TPIP_MAPPING_COMPILATION_FAILED",
                exception.getMessage(), Map.of("diagnostics", exception.diagnostics()));
    }

    @ExceptionHandler({PolicyTypeNotFoundException.class, IntegrationPolicyNotFoundException.class,
            IntegrationPolicyVersionNotFoundException.class})
    ResponseEntity<ApiErrorResponse> handlePolicyNotFound(RuntimeException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_POLICY_ASSET_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler({PolicyTypeAlreadyExistsException.class, PolicyCodeAlreadyExistsException.class})
    ResponseEntity<ApiErrorResponse> handlePolicyDuplicate(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_POLICY_ASSET_ALREADY_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(PolicyConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> handlePolicyConcurrent(PolicyConcurrentModificationException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_OPTIMISTIC_LOCK_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(PolicyVersionLifecycleException.class)
    ResponseEntity<ApiErrorResponse> handlePolicyLifecycle(PolicyVersionLifecycleException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_POLICY_VERSION_LIFECYCLE_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(PolicyCompilationException.class)
    ResponseEntity<ApiErrorResponse> handlePolicyCompilation(PolicyCompilationException exception) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "TPIP_POLICY_COMPILATION_FAILED",
                exception.getMessage(), Map.of("diagnostics", exception.diagnostics()));
    }

    @ExceptionHandler(OperationCodeAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleOperationDuplicate(OperationCodeAlreadyExistsException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_OPERATION_ALREADY_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(BindingCodeAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleBindingDuplicate(BindingCodeAlreadyExistsException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_BINDING_ALREADY_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler({OperationConcurrentModificationException.class, BindingConcurrentModificationException.class})
    ResponseEntity<ApiErrorResponse> handleAssetConcurrentModification(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_OPTIMISTIC_LOCK_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(BindingReferenceException.class)
    ResponseEntity<ApiErrorResponse> handleBindingReference(BindingReferenceException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_BINDING_REFERENCE_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CredentialRefNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleCredentialNotFound(CredentialRefNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_CREDENTIAL_REF_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CredentialRefAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleCredentialDuplicate(CredentialRefAlreadyExistsException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_CREDENTIAL_REF_ALREADY_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CredentialRefConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> handleCredentialConcurrentModification(
            CredentialRefConcurrentModificationException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_OPTIMISTIC_LOCK_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ProviderNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(ProviderNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_PROVIDER_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ProviderContractNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleContractNotFound(ProviderContractNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_PROVIDER_CONTRACT_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ProviderContractVersionNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleContractVersionNotFound(
            ProviderContractVersionNotFoundException exception) {
        return error(
                HttpStatus.NOT_FOUND,
                "TPIP_PROVIDER_CONTRACT_VERSION_NOT_FOUND",
                exception.getMessage(),
                Map.of());
    }

    @ExceptionHandler(EndpointNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleEndpointNotFound(EndpointNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TPIP_ENDPOINT_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ProviderCodeAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicate(ProviderCodeAlreadyExistsException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_PROVIDER_ALREADY_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ProviderContractCodeAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleContractDuplicate(
            ProviderContractCodeAlreadyExistsException exception) {
        return error(
                HttpStatus.CONFLICT,
                "TPIP_PROVIDER_CONTRACT_ALREADY_EXISTS",
                exception.getMessage(),
                Map.of());
    }

    @ExceptionHandler(ProviderContractVersionAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleContractVersionDuplicate(
            ProviderContractVersionAlreadyExistsException exception) {
        return error(
                HttpStatus.CONFLICT,
                "TPIP_PROVIDER_CONTRACT_VERSION_ALREADY_EXISTS",
                exception.getMessage(),
                Map.of());
    }

    @ExceptionHandler(ProviderConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> handleConcurrentModification(
            ProviderConcurrentModificationException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_OPTIMISTIC_LOCK_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ProviderContractConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> handleContractConcurrentModification(
            ProviderContractConcurrentModificationException exception) {
        return error(HttpStatus.CONFLICT, "TPIP_OPTIMISTIC_LOCK_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ContractVersionLifecycleException.class)
    ResponseEntity<ApiErrorResponse> handleContractVersionLifecycle(
            ContractVersionLifecycleException exception) {
        return error(
                HttpStatus.CONFLICT,
                "TPIP_CONTRACT_VERSION_LIFECYCLE_CONFLICT",
                exception.getMessage(),
                Map.of());
    }

    @ExceptionHandler(EndpointLifecycleException.class)
    ResponseEntity<ApiErrorResponse> handleEndpointLifecycle(EndpointLifecycleException exception) {
        return error(
                HttpStatus.CONFLICT,
                "TPIP_ENDPOINT_LIFECYCLE_CONFLICT",
                exception.getMessage(),
                Map.of());
    }

    @ExceptionHandler(EndpointCodeOwnershipException.class)
    ResponseEntity<ApiErrorResponse> handleEndpointOwnership(EndpointCodeOwnershipException exception) {
        return error(
                HttpStatus.CONFLICT,
                "TPIP_ENDPOINT_CODE_OWNERSHIP_CONFLICT",
                exception.getMessage(),
                Map.of());
    }

    @ExceptionHandler(EndpointCredentialMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleEndpointCredential(EndpointCredentialMismatchException exception) {
        return error(
                HttpStatus.CONFLICT,
                "TPIP_ENDPOINT_CREDENTIAL_MISMATCH",
                exception.getMessage(),
                Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "TPIP_VALIDATION_ERROR", "Request validation failed", details);
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    ResponseEntity<ApiErrorResponse> handleValidation(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, "TPIP_VALIDATION_ERROR", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return error(
                HttpStatus.BAD_REQUEST,
                "TPIP_INVALID_REQUEST_BODY",
                "Request body is malformed or contains an unsupported enum value",
                Map.of());
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status, String code, String message, Map<String, Object> details) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(Instant.now(), code, message, details));
    }
}
