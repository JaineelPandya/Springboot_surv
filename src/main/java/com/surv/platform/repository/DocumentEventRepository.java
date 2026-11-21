package com.surv.platform.repository;

import com.surv.platform.domain.DocumentEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentEventRepository extends JpaRepository<DocumentEvent, UUID> {
    Optional<DocumentEvent> findBySourceEventId(String sourceEventId);
    boolean existsBySourceEventId(String sourceEventId);
    Page<DocumentEvent> findByDocumentId(UUID documentId, Pageable pageable);
    Page<DocumentEvent> findByDocumentIdAndEventType(UUID documentId, String eventType, Pageable pageable);
    Page<DocumentEvent> findByOccurredAtBetween(Instant from, Instant to, Pageable pageable);
    Page<DocumentEvent> findByActorId(UUID actorId, Pageable pageable);
}
