package com.surv.platform.repository;

import com.surv.platform.domain.ViewingSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ViewingSessionRepository extends JpaRepository<ViewingSession, UUID> {
    Page<ViewingSession> findByDocumentId(UUID documentId, Pageable pageable);
    List<ViewingSession> findByViewerId(UUID viewerId);
    List<ViewingSession> findByDocumentIdAndViewerId(UUID documentId, UUID viewerId);
}
