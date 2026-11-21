package com.surv.platform.repository;

import com.surv.platform.domain.GmailMessageRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GmailMessageRecordRepository extends JpaRepository<GmailMessageRecord, UUID> {
    Optional<GmailMessageRecord> findByConnectionIdAndMessageId(UUID connectionId, String messageId);
    boolean existsByConnectionIdAndMessageId(UUID connectionId, String messageId);
    Page<GmailMessageRecord> findByConnectionId(UUID connectionId, Pageable pageable);
}
