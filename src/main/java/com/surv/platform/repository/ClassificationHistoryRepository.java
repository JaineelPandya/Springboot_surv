package com.surv.platform.repository;

import com.surv.platform.domain.ClassificationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClassificationHistoryRepository extends JpaRepository<ClassificationHistory, UUID> {
    List<ClassificationHistory> findByDocumentIdOrderByChangedAtDesc(UUID documentId);
    Page<ClassificationHistory> findByDocumentId(UUID documentId, Pageable pageable);
}
