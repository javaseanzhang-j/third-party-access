package com.ftk.tpip.control.api.error;

import static org.junit.jupiter.api.Assertions.*;

import com.ftk.tpip.release.domain.exception.GlobalImpactJobCommandConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalImpactJobCommandConflictHandlerTest {
    @Test
    void exposesStableConflictCodeAndRefreshHint() {
        var response = new GlobalExceptionHandler().handleGlobalImpactJobCommandConflict(
                new GlobalImpactJobCommandConflictException("Global impact job changed concurrently"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("TPIP_GLOBAL_IMPACT_JOB_COMMAND_CONFLICT", response.getBody().code());
        assertEquals(true, response.getBody().details().get("refreshRequired"));
    }
}
