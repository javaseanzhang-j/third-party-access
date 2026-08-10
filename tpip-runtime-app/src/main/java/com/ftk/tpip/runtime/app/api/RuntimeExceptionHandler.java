package com.ftk.tpip.runtime.app.api;

import com.ftk.tpip.runtime.BundleResolutionException;
import com.ftk.tpip.runtime.BundlePreflightException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RuntimeExceptionHandler {
    @ExceptionHandler(BundleResolutionException.class)
    ResponseEntity<RuntimeError> bundle(BundleResolutionException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new RuntimeError(
                "TPIP_RUNTIME_BUNDLE_UNAVAILABLE", exception.code().name(), exception.getMessage(), Instant.now()));
    }
    @ExceptionHandler(BundlePreflightException.class)
    ResponseEntity<RuntimePreflightError> preflight(BundlePreflightException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new RuntimePreflightError(
                "TPIP_RUNTIME_PREFLIGHT_FAILED", exception.getMessage(), exception.diagnostics(), Instant.now()));
    }
    public record RuntimeError(String code, String resolutionCode, String message, Instant timestamp) {}
    public record RuntimePreflightError(String code, String message, java.util.List<String> diagnostics,
            Instant timestamp) {}
}
