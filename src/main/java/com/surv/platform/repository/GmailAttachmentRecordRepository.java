package com.surv.platform.repository;

import com.surv.platform.domain.GmailAttachmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GmailAttachmentRecordRepository extends JpaRepository<GmailAttachmentRecord, UUID> {
    List<GmailAttachmentRecord> findByMessageRecordId(UUID messageRecordId);
    Optional<GmailAttachmentRecord> findByMessageRecordIdAndAttachmentId(UUID messageRecordId, String attachmentId);
    List<GmailAttachmentRecord> findByDownloadedFalse();
}
