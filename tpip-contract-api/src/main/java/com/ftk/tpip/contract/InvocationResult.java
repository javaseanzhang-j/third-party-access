package com.ftk.tpip.contract;

import java.util.Objects;

public record InvocationResult(boolean success, String code, String message) {

    public InvocationResult {
        code = Objects.requireNonNull(code, "code must not be null");
        message = Objects.requireNonNull(message, "message must not be null");
    }

    public static InvocationResult successful() {
        return new InvocationResult(true, "SUCCESS", "处理成功");
    }

    public static InvocationResult failure(String code, String message) {
        return new InvocationResult(false, code, message);
    }
}
