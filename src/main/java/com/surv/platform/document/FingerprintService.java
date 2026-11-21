package com.surv.platform.document;

import com.surv.platform.domain.Document;
import com.surv.platform.domain.DocumentFingerprint;
import com.surv.platform.domain.DocumentVersion;
import com.surv.platform.repository.DocumentFingerprintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class FingerprintService {

    private final DocumentFingerprintRepository fingerprintRepository;
    private final MimeTypeDetector mimeTypeDetector;

    /**
     * Compute SHA-256 fingerprint from raw bytes.
     */
    public String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Compute SHA-256 fingerprint, detect MIME type, and persist a DocumentFingerprint record.
     */
    @Transactional
    public DocumentFingerprint computeAndSave(Document document, DocumentVersion version,
                                               byte[] content, String filename) {
        String sha256 = computeSha256(content);
        String detectedMime = mimeTypeDetector.detect(content, filename);

        log.debug("Fingerprint computed: sha256={} mime={} size={} file={}",
                sha256, detectedMime, content.length, filename);

        DocumentFingerprint fingerprint = new DocumentFingerprint();
        fingerprint.setDocument(document);
        fingerprint.setVersion(version);
        fingerprint.setSha256Hash(sha256);
        fingerprint.setFileSizeBytes(content.length);
        fingerprint.setDetectedMime(detectedMime);
        fingerprint.setComputedAt(Instant.now());

        return fingerprintRepository.save(fingerprint);
    }
}
