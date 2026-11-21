package com.surv.platform.repository;

import com.surv.platform.domain.GmailOAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GmailOAuthTokenRepository extends JpaRepository<GmailOAuthToken, UUID> {
    Optional<GmailOAuthToken> findByConnectionId(UUID connectionId);
    Optional<GmailOAuthToken> findByGoogleEmail(String googleEmail);
    void deleteByConnectionId(UUID connectionId);
}
