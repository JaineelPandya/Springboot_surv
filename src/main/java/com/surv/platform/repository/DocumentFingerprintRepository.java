package com.surv.platform.repository;

import com.surv.platform.domain.DocumentFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentFingerprintRepository extends JpaRepository<DocumentFingerprint, UUID> {
    Optional<DocumentFingerprint> findByDocumentIdAndVersionId(UUID documentId, UUID versionId);
    List<DocumentFingerprint> findByDocumentId(UUID documentId);
    Optional<DocumentFingerprint> findBySha256Hash(String sha256Hash);
    boolean existsBySha256Hash(String sha256Hash);
}
