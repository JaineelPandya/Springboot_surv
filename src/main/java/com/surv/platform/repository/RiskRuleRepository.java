package com.surv.platform.repository;

import com.surv.platform.domain.RiskRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskRuleRepository extends JpaRepository<RiskRule, UUID> {
    Optional<RiskRule> findByName(String name);
    List<RiskRule> findByEnabledTrueOrderByPriorityAsc();
    List<RiskRule> findBySeverity(RiskRule.Severity severity);
}
