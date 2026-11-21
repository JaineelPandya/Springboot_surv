package com.surv.platform.repository;

import com.surv.platform.domain.IntegrationConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IntegrationConnectionRepository extends JpaRepository<IntegrationConnection, UUID> {
    List<IntegrationConnection> findByUserId(UUID userId);
    List<IntegrationConnection> findByUserIdAndProvider(UUID userId, String provider);
    List<IntegrationConnection> findByStatus(IntegrationConnection.ConnectionStatus status);
}
