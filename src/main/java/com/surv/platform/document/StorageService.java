package com.surv.platform.document;

import java.util.UUID;

/**
 * Abstraction over document binary storage backends.
 * Default implementation: local filesystem.
 * Future implementations: AWS S3, GCS, Azure Blob Storage.
 */
public interface StorageService {
    StorageResult store(UUID documentId, UUID versionId, String filename, byte[] content);
    byte[] retrieve(String storageKey);
    void delete(String storageKey);
}
