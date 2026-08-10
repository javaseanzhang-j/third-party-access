package com.ftk.tpip.catalog.domain.model;

public enum IdempotencyClass {
    IDEMPOTENT,
    IDEMPOTENT_WITH_KEY,
    NON_IDEMPOTENT,
    UNKNOWN
}
