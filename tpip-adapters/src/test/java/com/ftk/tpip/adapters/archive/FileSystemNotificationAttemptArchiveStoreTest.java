package com.ftk.tpip.adapters.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class FileSystemNotificationAttemptArchiveStoreTest {
    @Test
    void writesAtomicallyReadsBackAndRejectsPathsOutsideRoot() throws Exception {
        var root = Files.createTempDirectory("tpip-attempt-archive-");
        var store = new FileSystemNotificationAttemptArchiveStore(root, 1024);
        byte[] content = "{\"id\":1}\n".getBytes(StandardCharsets.UTF_8);
        var artifact = store.write("123e4567-e89b-12d3-a456-426614174000", content);

        assertArrayEquals(content, store.read(artifact.artifactUri()));
        assertArrayEquals(content, store.read(store.write(
                "123e4567-e89b-12d3-a456-426614174000", content).artifactUri()));
        assertThrows(IllegalStateException.class, () -> store.write(
                "123e4567-e89b-12d3-a456-426614174000", "different\n".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> store.read(root.resolveSibling("outside.ndjson").toUri().toString()));
        assertThrows(IllegalArgumentException.class,
                () -> store.write("../escape", content));
    }
}
