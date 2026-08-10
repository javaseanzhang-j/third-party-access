package com.ftk.tpip.control.application.provider;

record CanonicalContractContent(
        String requestSchema,
        String responseSchema,
        String errorSchema,
        String callbackSchema,
        String examples,
        String checksum) {}
