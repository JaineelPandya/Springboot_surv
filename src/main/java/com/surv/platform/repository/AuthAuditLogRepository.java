package com.surv.platform.repository;

import com.surv.platform.domain.AuthAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, UUID> {
    Page<AuthAuditLog> findByAccountId(UUID accountId, Pageable pageable);
    Page<AuthAuditLog> findByUsernameAndOccurredAtBetween(String username, Instant from, Instant to, Pageable pageable);
    Page<AuthAuditLog> findByAction(String action, Pageable pageable);
}
