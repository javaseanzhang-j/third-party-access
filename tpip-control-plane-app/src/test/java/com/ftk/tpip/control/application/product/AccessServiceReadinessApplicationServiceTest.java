package com.ftk.tpip.control.application.product;

import static com.ftk.tpip.control.application.product.AccessServiceReadinessApplicationService.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccessServiceReadinessApplicationServiceTest {
    @Test void blocksWhenAnyRequiredCheckIsBlocked() {
        var checks = List.of(check(CheckStatus.PASS), check(CheckStatus.BLOCK));
        assertEquals(ReadinessStatus.BLOCKED,
                AccessServiceReadinessApplicationService.summarize(checks, List.of()));
    }

    @Test void isReadyWithWarningsWhenOnlyOptionalChecksWarn() {
        var checks = List.of(check(CheckStatus.PASS), check(CheckStatus.WARN));
        assertEquals(ReadinessStatus.READY_WITH_WARNINGS,
                AccessServiceReadinessApplicationService.summarize(checks, List.of()));
    }

    @Test void propagatesBlockedTargetToServiceSummary() {
        var target = new TargetReadiness(1, "阿里云短信实现", "阿里云", "发送短信",
                ReadinessStatus.BLOCKED, List.of(check(CheckStatus.BLOCK)));
        assertEquals(ReadinessStatus.BLOCKED,
                AccessServiceReadinessApplicationService.summarize(List.of(check(CheckStatus.PASS)), List.of(target)));
    }

    @Test void isReadyWhenEveryCheckPasses() {
        assertEquals(ReadinessStatus.READY,
                AccessServiceReadinessApplicationService.summarize(List.of(check(CheckStatus.PASS)), List.of()));
    }

    private static ReadinessCheck check(CheckStatus status) {
        return new ReadinessCheck("TEST", "测试项", status, "说明", "/action");
    }
}
