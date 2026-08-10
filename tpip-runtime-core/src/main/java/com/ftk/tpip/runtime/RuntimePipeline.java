package com.ftk.tpip.runtime;

import com.ftk.tpip.contract.InvocationRequest;
import com.ftk.tpip.contract.InvocationResponse;

public interface RuntimePipeline {

    InvocationResponse invoke(String operationCode, InvocationRequest request);
}
