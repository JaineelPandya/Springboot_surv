package com.surv.platform.repository;

import com.surv.platform.domain.ClassificationRule;
import com.surv.platform.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassificationRuleRepository extends JpaRepository<ClassificationRule, UUID> {
    List<ClassificationRule> findByEnabledTrueOrderByPriorityAsc();
    List<ClassificationRule> findByTargetLevelAndEnabledTrue(Document.SensitivityLevel level);
    Optional<ClassificationRule> findByName(String name);
}
