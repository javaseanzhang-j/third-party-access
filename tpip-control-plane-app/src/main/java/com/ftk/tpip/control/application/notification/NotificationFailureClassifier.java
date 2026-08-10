package com.ftk.tpip.control.application.notification;

import com.ftk.tpip.release.domain.model.NotificationFailureClass;
import org.springframework.stereotype.Component;

@Component
public final class NotificationFailureClassifier {
    public NotificationFailureClass classify(String errorCode) {
        String normalized = errorCode == null ? "" : errorCode.trim().toUpperCase(java.util.Locale.ROOT);
        String code = normalized.isEmpty() ? "" : normalized.split("\\s+", 2)[0];
        if (code.contains("CIRCUIT_OPEN")) return NotificationFailureClass.CIRCUIT_OPEN;
        if (code.contains("RATE_LIMITED") || httpStatus(code) == 429) {
            return NotificationFailureClass.RATE_LIMITED;
        }
        if (isPermanent(code)) return NotificationFailureClass.PERMANENT;
        return NotificationFailureClass.TRANSIENT;
    }

    private static boolean isPermanent(String code) {
        if (code.contains("CREDENTIAL_REJECTED") || code.contains("SECURITY_POLICY_REJECTED")
                || code.contains("SERIALIZATION_FAILURE") || code.contains("CONFIGURATION_INVALID")
                || code.contains("ENDPOINT_INVALID") || code.contains("ENDPOINT_NOT_ALLOWED")
                || code.contains("MESSAGE_MISSING") || code.startsWith("SECRET_")
                || code.contains("REMOTE_REJECTED") || code.contains("SIGNING_FAILURE")
                || code.equals("NO_NOTIFICATION_PROVIDER")) return true;
        int status = httpStatus(code);
        return status >= 400 && status < 500 && status != 408 && status != 425 && status != 429;
    }

    private static int httpStatus(String code) {
        int separator = code.lastIndexOf('_');
        if (separator < 0 || separator == code.length() - 1) return -1;
        try { return Integer.parseInt(code.substring(separator + 1)); }
        catch (NumberFormatException ignored) { return -1; }
    }
}
