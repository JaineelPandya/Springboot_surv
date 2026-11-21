package com.surv.platform.repository;

import com.surv.platform.domain.AlertInvestigation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertInvestigationRepository extends JpaRepository<AlertInvestigation, UUID> {
    List<AlertInvestigation> findByAlertIdOrderByActionAtDesc(UUID alertId);
    List<AlertInvestigation> findByInvestigatorId(UUID investigatorId);
}
