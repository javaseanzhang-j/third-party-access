package com.ftk.tpip.runtime;

public final class BundleResolutionException extends RuntimeException {
    private final BundleResolutionCode code;

    public BundleResolutionException(BundleResolutionCode code, String message) {
        super(message);
        this.code = code;
    }

    public BundleResolutionException(BundleResolutionCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public BundleResolutionCode code() {
        return code;
    }
}
