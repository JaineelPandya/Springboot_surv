package com.surv.platform.repository;

import com.surv.platform.domain.AppAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppAccountRepository extends JpaRepository<AppAccount, UUID> {
    Optional<AppAccount> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<AppAccount> findByUserId(UUID userId);
}
