package com.surv.platform.repository;

import com.surv.platform.domain.RiskRule;
import com.surv.platform.domain.SecurityAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, UUID> {
    Page<SecurityAlert> findByDocumentId(UUID documentId, Pageable pageable);
    Page<SecurityAlert> findByStatus(SecurityAlert.AlertStatus status, Pageable pageable);
    Page<SecurityAlert> findBySeverity(RiskRule.Severity severity, Pageable pageable);
    long countByStatus(SecurityAlert.AlertStatus status);
}
