package com.ftk.tpip.control.application.integration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

record SchemaAssetReference(Kind kind, long definitionId, long versionId) {
    private static final Pattern FORMAT = Pattern.compile("^(canonical|provider)-contract-version:([1-9]\\d*):([1-9]\\d*)$");
    enum Kind { CANONICAL, PROVIDER }
    static SchemaAssetReference parse(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        Matcher matcher = FORMAT.matcher(value.trim());
        if (!matcher.matches()) throw new IllegalArgumentException(field + " must use canonical-contract-version:{contractId}:{versionId} or provider-contract-version:{contractId}:{versionId}");
        return new SchemaAssetReference("canonical".equals(matcher.group(1)) ? Kind.CANONICAL : Kind.PROVIDER,
                Long.parseLong(matcher.group(2)), Long.parseLong(matcher.group(3)));
    }
    String externalForm() {
        return (kind == Kind.CANONICAL ? "canonical" : "provider") + "-contract-version:" + definitionId + ":" + versionId;
    }
}
