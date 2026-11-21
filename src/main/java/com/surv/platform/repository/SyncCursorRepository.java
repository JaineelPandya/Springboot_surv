package com.surv.platform.repository;

import com.surv.platform.domain.SyncCursor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyncCursorRepository extends JpaRepository<SyncCursor, UUID> {
    Optional<SyncCursor> findByConnectionIdAndCursorType(UUID connectionId, String cursorType);
}
