package com.surv.platform.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Local filesystem storage implementation.
 * Stores files at: {basePath}/{documentId}/{versionId}/{sanitized-filename}
 * Replace with cloud implementation (S3, GCS) in production.
 */
@Slf4j
@Service
public class LocalStorageService implements StorageService {

    @Value("${app.storage.base-path:/tmp/surveillance-storage}")
    private String basePath;

    @Override
    public StorageResult store(UUID documentId, UUID versionId, String filename, byte[] content) {
        try {
            Path dir = Paths.get(basePath, documentId.toString(), versionId.toString());
            Files.createDirectories(dir);
            String safeFilename = sanitizeFilename(filename);
            Path filePath = dir.resolve(safeFilename);
            Files.write(filePath, content);
            String storageKey = documentId + "/" + versionId + "/" + safeFilename;
            log.debug("Stored file: key={} size={}", storageKey, content.length);
            return new StorageResult(storageKey, content.length);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file: " + filename, e);
        }
    }

    @Override
    public byte[] retrieve(String storageKey) {
        try {
            return Files.readAllBytes(Paths.get(basePath, storageKey));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to retrieve file at key: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            boolean deleted = Files.deleteIfExists(Paths.get(basePath, storageKey));
            if (deleted) log.debug("Deleted storage key: {}", storageKey);
        } catch (IOException e) {
            log.warn("Failed to delete storage key {}: {}", storageKey, e.getMessage());
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
