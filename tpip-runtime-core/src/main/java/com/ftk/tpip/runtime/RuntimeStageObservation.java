package com.ftk.tpip.runtime;

import java.time.Duration;
import java.util.Objects;

public record RuntimeStageObservation(RuntimeStage stage, Duration duration, boolean success) {
    public RuntimeStageObservation {
        stage = Objects.requireNonNull(stage, "stage must not be null");
        duration = Objects.requireNonNull(duration, "duration must not be null");
    }
}
