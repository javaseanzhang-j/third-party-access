package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

class GlobalImpactJobMaintenanceControllerContractTest {
    @Test void exposesCancellationPreviewAndControlledRunPaths() throws Exception{
        String base=GlobalImpactJobMaintenanceController.class.getAnnotation(RequestMapping.class).value()[0];
        Method cancel=GlobalImpactJobMaintenanceController.class.getMethod("cancel",String.class,
                GlobalImpactJobMaintenanceController.CancelCommand.class,String.class);
        Method candidates=GlobalImpactJobMaintenanceController.class.getMethod("candidates",int.class,int.class);
        Method run=GlobalImpactJobMaintenanceController.class.getMethod("run",
                GlobalImpactJobMaintenanceController.MaintenanceCommand.class,String.class);
        Method receipt=GlobalImpactJobMaintenanceController.class.getMethod("receipt",String.class);
        assertEquals("/control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}:cancel",
                base+cancel.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("/maintenance-candidates",candidates.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/maintenance:run",run.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("/{jobId}/purge-receipt",receipt.getAnnotation(GetMapping.class).value()[0]);
    }
}
