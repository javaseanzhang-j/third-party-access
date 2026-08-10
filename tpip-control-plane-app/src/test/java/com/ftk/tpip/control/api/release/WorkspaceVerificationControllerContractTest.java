package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

class WorkspaceVerificationControllerContractTest {
    @Test
    void verificationCommandContainsReferencesOnlyAndCannotDeclareResult() {
        String[] components = Arrays.stream(WorkspaceVerificationController.VerifyWorkspace.class.getRecordComponents())
                .map(component -> component.getName()).toArray(String[]::new);
        assertArrayEquals(new String[] {"fixtureSuiteVersionId", "rowVersion"}, components);
    }

    @Test
    void serverVerificationOwnsTheOnlyWriteEndpoint() throws Exception {
        PostMapping mapping = WorkspaceVerificationController.class
                .getDeclaredMethod("verify", long.class, WorkspaceVerificationController.VerifyWorkspace.class,
                        String.class)
                .getAnnotation(PostMapping.class);
        assertArrayEquals(new String[] {"/workspaces/{workspaceId}:verify"}, mapping.value());
        assertTrue(Arrays.stream(ReleaseController.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("verify")));
    }

    @Test
    void failedJobHasExplicitRetryEndpoint() throws Exception {
        PostMapping mapping = WorkspaceVerificationController.class
                .getDeclaredMethod("retry", long.class, WorkspaceVerificationController.RetryVerification.class,
                        String.class)
                .getAnnotation(PostMapping.class);
        assertArrayEquals(new String[] {"/verification-jobs/{runId}:retry"}, mapping.value());
    }
}
