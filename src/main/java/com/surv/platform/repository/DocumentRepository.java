package com.surv.platform.repository;

import com.surv.platform.domain.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findBySourceDocumentId(String sourceDocumentId);
    boolean existsBySourceDocumentId(String sourceDocumentId);
    Page<Document> findByOwnerId(UUID ownerId, Pageable pageable);
    Page<Document> findBySensitivityLevel(Document.SensitivityLevel sensitivityLevel, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.owner.id = :ownerId AND d.status = :status")
    List<Document> findByOwnerIdAndStatus(@Param("ownerId") UUID ownerId, @Param("status") Document.DocumentStatus status);
}
