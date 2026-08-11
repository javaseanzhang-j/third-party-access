package com.ftk.tpip.mcp.application;

import org.springframework.scheduling.annotation.Scheduled;

public final class McpCatalogRefreshScheduler {

    private final McpRuntimeCatalogRefresher refresher;

    public McpCatalogRefreshScheduler(McpRuntimeCatalogRefresher refresher) {
        this.refresher = refresher;
    }

    @Scheduled(fixedDelayString = "${tpip.mcp.catalog-refresh-interval:30s}",
            initialDelayString = "${tpip.mcp.catalog-refresh-interval:30s}")
    public void refresh() {
        try {
            refresher.refresh("SCHEDULED");
        } catch (RuntimeException ignored) {
            // Refresher keeps the last known-good runtime snapshot and records the failure status.
        }
    }
}
