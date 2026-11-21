package com.surv.platform.document;

import com.surv.platform.common.exception.BusinessException;
import com.surv.platform.common.exception.ResourceNotFoundException;
import com.surv.platform.domain.*;
import com.surv.platform.document.dto.*;
import com.surv.platform.dto.DocumentDto;
import com.surv.platform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository               documentRepository;
    private final DocumentVersionRepository        documentVersionRepository;
    private final DocumentRecipientRepository      documentRecipientRepository;
    private final UserRepository                   userRepository;
    private final AppAccountRepository             appAccountRepository;
    private final ClassificationHistoryRepository  classificationHistoryRepository;
    private final FingerprintService               fingerprintService;
    private final ClassificationEngine             classificationEngine;
    private final MimeTypeDetector                 mimeTypeDetector;
    private final StorageService                   storageService;

    // ------------------------------------------------------------------ register
    @Transactional
    public DocumentUploadResult registerDocument(DocumentRegisterRequest request,
                                                  byte[] fileContent,
                                                  String filename,
                                                  Authentication auth) {
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.ownerId()));

        if (documentRepository.existsBySourceDocumentId(request.sourceDocumentId())) {
            throw new BusinessException("Document already registered with sourceDocumentId: "
                    + request.sourceDocumentId(), HttpStatus.CONFLICT);
        }

        // 1. Inspect bytes — NEVER trust extension or declared mime
        String detectedMime = mimeTypeDetector.detect(fileContent, filename);
        if (!mimeTypeDetector.isSafeContentType(detectedMime)) {
            throw new BusinessException("Unsafe file type detected: " + detectedMime, HttpStatus.BAD_REQUEST);
        }

        // 2. Classification: manual > rule-based > default INTERNAL
        Document.SensitivityLevel level;
        String classificationMethod;
        ClassificationRule matchedRule = null;

        if (request.sensitivityLevel() != null) {
            level = request.sensitivityLevel();
            classificationMethod = "MANUAL";
        } else {
            var match = classificationEngine.evaluate(request.title(), detectedMime, request.metadata());
            if (match.isPresent()) {
                level = match.get().level();
                matchedRule = match.get().rule();
                classificationMethod = "RULE_BASED";
            } else {
                level = Document.SensitivityLevel.INTERNAL;
                classificationMethod = "DEFAULT";
            }
        }

        // 3. Persist document
        Document document = new Document();
        document.setOwner(owner);
        document.setSourceDocumentId(request.sourceDocumentId());
        document.setTitle(request.title());
        document.setMimeType(detectedMime);
        document.setSensitivityLevel(level);
        document.setStatus(Document.DocumentStatus.ACTIVE);
        document.setFileSizeBytes((long) fileContent.length);
        document.setMetadata(request.metadata());
        document = documentRepository.save(document);

        // 4. Version 1
        DocumentVersion version = new DocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(1);
        version.setFileSizeBytes((long) fileContent.length);
        version.setChangeSummary("Initial registration");
        version = documentVersionRepository.save(version);

        // 5. Store binary
        storageService.store(document.getId(), version.getId(), filename, fileContent);

        // 6. Fingerprint
        DocumentFingerprint fp = fingerprintService.computeAndSave(document, version, fileContent, filename);
        version.setChecksum(fp.getSha256Hash());
        documentVersionRepository.save(version);

        // 7. Classification history
        recordClassificationHistory(document, null, level, classificationMethod, matchedRule, auth,
                "Initial document registration");

        log.info("Document registered: id={} title='{}' level={} method={} mime={}",
                document.getId(), request.title(), level, classificationMethod, detectedMime);

        return new DocumentUploadResult(document.getId(), version.getId(),
                fp.getSha256Hash(), detectedMime, level, classificationMethod,
                fileContent.length, Instant.now());
    }

    // ------------------------------------------------------------------ get
    @Transactional(readOnly = true)
    public DocumentDto getDocument(UUID documentId, Authentication auth) {
        Document document = getDocumentOrThrow(documentId);
        checkReadAccess(document, auth);
        return DocumentDto.from(document);
    }

    // ------------------------------------------------------------------ list
    @Transactional(readOnly = true)
    public Page<DocumentDto> listDocuments(UUID ownerId, Pageable pageable, Authentication auth) {
        if (!hasElevatedRole(auth)) {
            // EMPLOYEE sees only their own documents
            AppAccount account = getAccountFromAuth(auth);
            UUID callerUserId = account.getUser().getId();
            if (ownerId != null && !ownerId.equals(callerUserId)) {
                throw new BusinessException("Cannot list other users' documents", HttpStatus.FORBIDDEN);
            }
            return documentRepository.findByOwnerId(callerUserId, pageable).map(DocumentDto::from);
        }
        if (ownerId != null) {
            return documentRepository.findByOwnerId(ownerId, pageable).map(DocumentDto::from);
        }
        return documentRepository.findAll(pageable).map(DocumentDto::from);
    }

    // ------------------------------------------------------------------ reclassify
    @Transactional
    public DocumentDto reclassify(UUID documentId, ClassifyRequest request, Authentication auth) {
        Document document = getDocumentOrThrow(documentId);
        // Only elevated roles can reclassify (owner can't downgrade their own doc)
        if (!hasElevatedRole(auth)) {
            throw new BusinessException("Insufficient permissions to reclassify", HttpStatus.FORBIDDEN);
        }
        Document.SensitivityLevel previous = document.getSensitivityLevel();
        document.setSensitivityLevel(request.newLevel());
        documentRepository.save(document);
        recordClassificationHistory(document, previous, request.newLevel(), "MANUAL", null, auth, request.reason());
        log.info("Document {} reclassified {} -> {} by {}", documentId, previous, request.newLevel(), auth.getName());
        return DocumentDto.from(document);
    }

    // ------------------------------------------------------------------ add version
    @Transactional
    public DocumentDto addVersion(UUID documentId, byte[] fileContent, String filename,
                                   String changeSummary, Authentication auth) {
        Document document = getDocumentOrThrow(documentId);
        checkOwnershipOrElevatedRole(document, auth);

        String detectedMime = mimeTypeDetector.detect(fileContent, filename);
        if (!mimeTypeDetector.isSafeContentType(detectedMime)) {
            throw new BusinessException("Unsafe file type detected: " + detectedMime, HttpStatus.BAD_REQUEST);
        }

        int nextVersion = documentVersionRepository
                .findTopByDocumentIdOrderByVersionNumberDesc(documentId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(2);

        DocumentVersion version = new DocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(nextVersion);
        version.setFileSizeBytes((long) fileContent.length);
        version.setChangeSummary(changeSummary != null ? changeSummary : "New version");
        version = documentVersionRepository.save(version);

        storageService.store(documentId, version.getId(), filename, fileContent);
        DocumentFingerprint fp = fingerprintService.computeAndSave(document, version, fileContent, filename);
        version.setChecksum(fp.getSha256Hash());
        documentVersionRepository.save(version);

        // Auto-upgrade classification if rule engine finds a higher level on new version
        classificationEngine.evaluate(document.getTitle(), detectedMime, document.getMetadata())
                .ifPresent(match -> {
                    if (match.level().ordinal() > document.getSensitivityLevel().ordinal()) {
                        Document.SensitivityLevel prev = document.getSensitivityLevel();
                        document.setSensitivityLevel(match.level());
                        documentRepository.save(document);
                        recordClassificationHistory(document, prev, match.level(),
                                "RULE_BASED_UPGRADE", match.rule(), auth,
                                "Auto-upgrade on new version upload");
                    }
                });

        return DocumentDto.from(document);
    }

    // ------------------------------------------------------------------ recipients
    @Transactional
    public void addRecipient(UUID documentId, AddRecipientRequest request, Authentication auth) {
        Document document = getDocumentOrThrow(documentId);
        checkOwnershipOrElevatedRole(document, auth);

        userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.userId()));

        if (documentRecipientRepository.existsByDocumentIdAndUserId(documentId, request.userId())) {
            throw new BusinessException("Recipient already has access", HttpStatus.CONFLICT);
        }

        DocumentRecipient dr = new DocumentRecipient();
        dr.setDocument(document);
        dr.setUser(userRepository.getReferenceById(request.userId()));
        dr.setAccessLevel(request.accessLevel());
        dr.setGrantedAt(Instant.now());
        dr.setExpiresAt(request.expiresAt());
        if (auth != null) {
            dr.setGrantedBy(auth.getName());
        }
        documentRecipientRepository.save(dr);
    }

    // ------------------------------------------------------------------ helpers
    private Document getDocumentOrThrow(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", id));
    }

    private void checkOwnershipOrElevatedRole(Document document, Authentication auth) {
        if (hasElevatedRole(auth)) return;
        AppAccount account = getAccountFromAuth(auth);
        if (!document.getOwner().getId().equals(account.getUser().getId())) {
            throw new BusinessException("You do not own this document", HttpStatus.FORBIDDEN);
        }
    }

    private void checkReadAccess(Document document, Authentication auth) {
        if (hasElevatedRole(auth)) return;
        AppAccount account = getAccountFromAuth(auth);
        UUID userId = account.getUser().getId();
        boolean isOwner     = document.getOwner().getId().equals(userId);
        boolean isRecipient = documentRecipientRepository.existsByDocumentIdAndUserId(document.getId(), userId);
        if (!isOwner && !isRecipient) {
            throw new BusinessException("Access denied to document", HttpStatus.FORBIDDEN);
        }
    }

    private boolean hasElevatedRole(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                a.getAuthority().equals("ROLE_COMPLIANCE_OFFICER") ||
                a.getAuthority().equals("ROLE_SECURITY_ANALYST") ||
                a.getAuthority().equals("ROLE_AUDITOR"));
    }

    private AppAccount getAccountFromAuth(Authentication auth) {
        return appAccountRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.UNAUTHORIZED));
    }

    private void recordClassificationHistory(Document document,
                                              Document.SensitivityLevel previous,
                                              Document.SensitivityLevel newLevel,
                                              String method,
                                              ClassificationRule rule,
                                              Authentication auth,
                                              String reason) {
        try {
            ClassificationHistory history = new ClassificationHistory();
            history.setDocument(document);
            history.setPreviousLevel(previous);
            history.setNewLevel(newLevel);
            history.setClassificationMethod(method);
            history.setRule(rule);
            history.setReason(reason);
            history.setChangedAt(Instant.now());
            if (auth != null) {
                history.setChangedByUsername(auth.getName());
                appAccountRepository.findByUsername(auth.getName())
                        .ifPresent(acc -> history.setChangedBy(acc.getUser()));
            }
            classificationHistoryRepository.save(history);
        } catch (Exception e) {
            log.warn("Failed to record classification history: {}", e.getMessage());
        }
    }
}
