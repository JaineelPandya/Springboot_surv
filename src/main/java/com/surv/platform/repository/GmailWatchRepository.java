package com.surv.platform.repository;

import com.surv.platform.domain.GmailWatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GmailWatchRepository extends JpaRepository<GmailWatch, UUID> {
    Optional<GmailWatch> findByConnectionIdAndActiveTrue(UUID connectionId);
    Optional<GmailWatch> findByGoogleEmailAndActiveTrue(String googleEmail);
    List<GmailWatch> findByActiveTrueAndExpiresAtBefore(Instant expiryThreshold);
}
