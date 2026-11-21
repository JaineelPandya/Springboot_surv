package com.surv.platform.repository;

import com.surv.platform.domain.DocumentRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRecipientRepository extends JpaRepository<DocumentRecipient, UUID> {
    List<DocumentRecipient> findByDocumentId(UUID documentId);
    List<DocumentRecipient> findByUserId(UUID userId);
    Optional<DocumentRecipient> findByDocumentIdAndUserId(UUID documentId, UUID userId);
    boolean existsByDocumentIdAndUserId(UUID documentId, UUID userId);
}
