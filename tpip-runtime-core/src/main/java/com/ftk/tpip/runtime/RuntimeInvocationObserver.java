package com.ftk.tpip.runtime;

@FunctionalInterface
public interface RuntimeInvocationObserver {
    void onCompleted(RuntimeInvocationObservation observation);

    static RuntimeInvocationObserver noop() {
        return observation -> { };
    }
}
