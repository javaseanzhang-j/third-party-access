package com.ftk.tpip.release.domain.model;

public enum DeploymentStatus {
    PENDING,
    PREHEATING,
    READY,
    PREHEAT_FAILED,
    ACTIVE,
    DEPRECATED,
    ROLLED_BACK
}
