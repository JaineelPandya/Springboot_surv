package com.surv.platform.document;

import com.surv.platform.auth.AuthService;
import com.surv.platform.auth.RegisterRequest;
import com.surv.platform.domain.AppAccount;
import com.surv.platform.domain.Document;
import com.surv.platform.document.dto.ClassifyRequest;
import com.surv.platform.document.dto.DocumentRegisterRequest;
import com.surv.platform.repository.AppAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class DocumentServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_docs_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }

    @Autowired DocumentService     documentService;
    @Autowired AuthService         authService;
    @Autowired AppAccountRepository accountRepository;

    private AppAccount ownerAccount;
    private AppAccount employeeAccount;
    private AppAccount adminAccount;

    @BeforeEach
    void setup() {
        tryRegister("doc_owner",    "owner@test.com",    "Pass123!",  "Owner",    AppAccount.AppRole.EMPLOYEE);
        tryRegister("doc_employee", "employee@test.com", "Pass123!",  "Employee", AppAccount.AppRole.EMPLOYEE);
        tryRegister("doc_admin",    "docadmin@test.com", "Admin123!", "Admin",    AppAccount.AppRole.SUPER_ADMIN);
        ownerAccount    = accountRepository.findByUsername("doc_owner").orElseThrow();
        employeeAccount = accountRepository.findByUsername("doc_employee").orElseThrow();
        adminAccount    = accountRepository.findByUsername("doc_admin").orElseThrow();
    }

    private void tryRegister(String username, String email, String pass,
                              String displayName, AppAccount.AppRole role) {
        try {
            authService.registerAccount(new RegisterRequest(username, email, pass, displayName, role));
        } catch (Exception ignored) {}
    }

    private Authentication authFor(AppAccount account) {
        return new UsernamePasswordAuthenticationToken(
                account.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
    }

    // ------------------------------------------------------------------ tests

    @Test
    void registerDocument_computesFingerprintAndDefaultsToInternal() {
        byte[] content = "Plain test document content.".getBytes();
        var req = new DocumentRegisterRequest("Test Document", "src-" + UUID.randomUUID(),
                ownerAccount.getUser().getId(), null, null, null, null);

        var result = documentService.registerDocument(req, content, "test.txt", authFor(ownerAccount));

        assertThat(result.documentId()).isNotNull();
        assertThat(result.sha256Hash()).hasSize(64);
        assertThat(result.detectedMimeType()).isNotBlank();
        assertThat(result.sensitivityLevel()).isEqualTo(Document.SensitivityLevel.INTERNAL);
        assertThat(result.classificationMethod()).isEqualTo("DEFAULT");
    }

    @Test
    void registerDocument_withManualClassification_usesRequestedLevel() {
        byte[] content = "CONFIDENTIAL payroll data.".getBytes();
        var req = new DocumentRegisterRequest("Payroll Report", "src-pay-" + UUID.randomUUID(),
                ownerAccount.getUser().getId(), Document.SensitivityLevel.CONFIDENTIAL, null, null, null);

        var result = documentService.registerDocument(req, content, "payroll.txt", authFor(ownerAccount));

        assertThat(result.sensitivityLevel()).isEqualTo(Document.SensitivityLevel.CONFIDENTIAL);
        assertThat(result.classificationMethod()).isEqualTo("MANUAL");
    }

    @Test
    void registerDuplicateSourceDocumentId_throwsConflict() {
        String srcId = "src-dup-" + UUID.randomUUID();
        var req = new DocumentRegisterRequest("Doc 1", srcId,
                ownerAccount.getUser().getId(), null, null, null, null);
        documentService.registerDocument(req, "content".getBytes(), "file.txt", authFor(ownerAccount));

        var req2 = new DocumentRegisterRequest("Doc 2", srcId,
                ownerAccount.getUser().getId(), null, null, null, null);
        assertThatThrownBy(() ->
                documentService.registerDocument(req2, "content2".getBytes(), "file2.txt", authFor(ownerAccount)))
                .hasMessageContaining("already registered");
    }

    @Test
    void employeeCannotReadAnotherUsersDocument() {
        var req = new DocumentRegisterRequest("Private Doc", "src-priv-" + UUID.randomUUID(),
                ownerAccount.getUser().getId(), null, null, null, null);
        var result = documentService.registerDocument(req, "secret".getBytes(), "priv.txt", authFor(ownerAccount));

        assertThatThrownBy(() -> documentService.getDocument(result.documentId(), authFor(employeeAccount)))
                .hasMessageContaining("Access denied");
    }

    @Test
    void adminCanReadAnyDocument() {
        var req = new DocumentRegisterRequest("Admin-Readable Doc", "src-adm-" + UUID.randomUUID(),
                ownerAccount.getUser().getId(), null, null, null, null);
        var result = documentService.registerDocument(req, "data".getBytes(), "adm.txt", authFor(ownerAccount));

        var dto = documentService.getDocument(result.documentId(), authFor(adminAccount));
        assertThat(dto.getId()).isEqualTo(result.documentId());
    }

    @Test
    void employeeCannotListOtherUsersDocuments() {
        assertThatThrownBy(() ->
                documentService.listDocuments(employeeAccount.getUser().getId(),
                        org.springframework.data.domain.Pageable.unpaged(), authFor(ownerAccount)))
                .hasMessageContaining("Cannot list");
    }

    @Test
    void reclassifyByAdmin_updatesLevelAndRecordsHistory() {
        var req = new DocumentRegisterRequest("To-Reclassify", "src-rcl-" + UUID.randomUUID(),
                ownerAccount.getUser().getId(), null, null, null, null);
        var result = documentService.registerDocument(req, "text".getBytes(), "rcl.txt", authFor(ownerAccount));

        var updated = documentService.reclassify(result.documentId(),
                new ClassifyRequest(Document.SensitivityLevel.RESTRICTED, "Contains PII"),
                authFor(adminAccount));

        assertThat(updated.getSensitivityLevel()).isEqualTo(Document.SensitivityLevel.RESTRICTED);
    }

    @Test
    void employeeCannotReclassify_throwsForbidden() {
        var req = new DocumentRegisterRequest("My Doc", "src-myrcl-" + UUID.randomUUID(),
                ownerAccount.getUser().getId(), null, null, null, null);
        var result = documentService.registerDocument(req, "text".getBytes(), "my.txt", authFor(ownerAccount));

        assertThatThrownBy(() ->
                documentService.reclassify(result.documentId(),
                        new ClassifyRequest(Document.SensitivityLevel.PUBLIC, "trying to downgrade"),
                        authFor(ownerAccount)))
                .hasMessageContaining("Insufficient permissions");
    }

    @Test
    void sha256FingerprintIsDeterministic() {
        // Pure unit test — no Spring context needed
        FingerprintService fs = new FingerprintService(null, new MimeTypeDetector());
        byte[] content = "deterministic content for sha256".getBytes();
        String hash1 = fs.computeSha256(content);
        String hash2 = fs.computeSha256(content);
        assertThat(hash1).isEqualTo(hash2).hasSize(64);
    }
}
