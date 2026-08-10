package com.ftk.tpip.adapters.archive;

import com.ftk.tpip.release.domain.service.NotificationAttemptArchiveStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class FileSystemNotificationAttemptArchiveStore implements NotificationAttemptArchiveStore {
    private final Path root;
    private final long maximumBytes;

    public FileSystemNotificationAttemptArchiveStore(Path root, long maximumBytes) {
        if (root == null) throw new IllegalArgumentException("archive root is required");
        if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
        this.root = root.toAbsolutePath().normalize();
        this.maximumBytes = maximumBytes;
    }

    @Override public StoredArchive write(String batchCode, byte[] content) {
        if (batchCode == null || !batchCode.matches("[0-9a-f-]{36}") || content == null || content.length == 0) {
            throw new IllegalArgumentException("archive content or batchCode is invalid");
        }
        if (content.length > maximumBytes) throw new IllegalArgumentException("archive content is too large");
        Path target = resolve(batchCode + ".ndjson");
        try {
            Files.createDirectories(root);
            if (Files.exists(target)) return existing(target, content);
            Path temporary = Files.createTempFile(root, batchCode + "-", ".tmp");
            try {
                Files.write(temporary, content);
                try {
                    Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, target);
                } catch (java.nio.file.FileAlreadyExistsException exception) {
                    return existing(target, content);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new StoredArchive(target.toUri().toString(), content.length);
        } catch (IOException exception) {
            throw new IllegalStateException("attempt archive artifact cannot be written", exception);
        }
    }

    private StoredArchive existing(Path target, byte[] content) throws IOException {
        if (Files.size(target) > maximumBytes || !Arrays.equals(Files.readAllBytes(target), content)) {
            throw new IllegalStateException("attempt archive artifact is immutable and already exists");
        }
        return new StoredArchive(target.toUri().toString(), content.length);
    }

    @Override public byte[] read(String artifactUri) {
        if (artifactUri == null || artifactUri.isBlank()) throw new IllegalArgumentException("artifactUri is required");
        try {
            Path target = Path.of(java.net.URI.create(artifactUri)).toAbsolutePath().normalize();
            if (!target.startsWith(root) || !target.getFileName().toString().endsWith(".ndjson")) {
                throw new IllegalArgumentException("artifactUri is outside the configured archive root");
            }
            if (Files.size(target) > maximumBytes) throw new IllegalArgumentException("archive artifact is too large");
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new IllegalStateException("attempt archive artifact cannot be read", exception);
        }
    }

    @Override public StorageHealth probe() {
        try {
            Files.createDirectories(root);
            boolean accessible = Files.isDirectory(root) && Files.isReadable(root) && Files.isWritable(root);
            return new StorageHealth("FILESYSTEM", accessible, accessible, false, false,
                    accessible ? "Archive directory is readable and writable" : "Archive directory is inaccessible");
        } catch (IOException exception) {
            return new StorageHealth("FILESYSTEM", false, false, false, false,
                    "Archive directory probe failed: " + exception.getClass().getSimpleName());
        }
    }

    private Path resolve(String name) {
        Path result = root.resolve(name).normalize();
        if (!result.startsWith(root)) throw new IllegalArgumentException("archive path escapes configured root");
        return result;
    }
}
