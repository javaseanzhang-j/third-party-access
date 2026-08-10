package com.ftk.tpip.release.domain.exception;

/** A requested Global Impact command no longer matches the current job state or row version. */
public final class GlobalImpactJobCommandConflictException extends RuntimeException {
    public GlobalImpactJobCommandConflictException(String message) {
        super(message);
    }
}
