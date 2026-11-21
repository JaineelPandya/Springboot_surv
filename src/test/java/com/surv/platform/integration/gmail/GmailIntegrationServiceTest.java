package com.surv.platform.integration.gmail;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.surv.platform.document.dto.DocumentUploadResult;
import com.surv.platform.domain.*;
import com.surv.platform.integration.gmail.client.GmailClientProvider;
import com.surv.platform.integration.gmail.dto.GmailSyncResultDto;
import com.surv.platform.integration.gmail.dto.PubSubNotificationRequest;
import com.surv.platform.integration.gmail.service.GmailSyncService;
import com.surv.platform.integration.gmail.service.GmailWatchService;
import com.surv.platform.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest
class GmailIntegrationServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_gmail_db")
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

    @MockBean
    private GmailClientProvider clientProvider;

    @Autowired private GmailSyncService syncService;
    @Autowired private GmailWatchService watchService;
    @Autowired private UserRepository userRepository;
    @Autowired private IntegrationConnectionRepository connectionRepository;
    @Autowired private GmailMessageRecordRepository messageRecordRepository;
    @Autowired private GmailAttachmentRecordRepository attachmentRecordRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentEventRepository documentEventRepository;
    @Autowired private GmailWatchRepository watchRepository;

    private IntegrationConnection connection;
    private Gmail mockGmail;
    private Gmail.Users mockUsers;
    private Gmail.Users.Messages mockMessages;
    private Gmail.Users.Messages.List mockMessagesList;
    private Gmail.Users.Messages.Get mockMessagesGet;
    private Gmail.Users.Messages.Attachments mockAttachments;
    private Gmail.Users.Messages.Attachments.Get mockAttachmentsGet;
    private Gmail.Users.Watch mockWatchCall;

    @BeforeEach
    void setUp() throws IOException {
        User user = userRepository.findByEmail("surv_test_owner@example.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail("surv_test_owner@example.com");
                    u.setDisplayName("Test Owner");
                    u.setStatus(User.UserStatus.ACTIVE);
                    return userRepository.save(u);
                });

        connection = connectionRepository.findByUserIdAndProvider(user.getId(), "GMAIL")
                .stream().findFirst()
                .orElseGet(() -> {
                    IntegrationConnection c = new IntegrationConnection();
                    c.setUser(user);
                    c.setProvider("GMAIL");
                    c.setAccountIdentifier("audited.employee@workspace.com");
                    c.setStatus(IntegrationConnection.ConnectionStatus.ACTIVE);
                    return connectionRepository.save(c);
                });

        // Setup mock Gmail client hierarchy
        mockGmail = mock(Gmail.class, Answers.RETURNS_DEEP_STUBS);
        mockUsers = mockGmail.users();
        mockMessages = mockUsers.messages();
        mockMessagesList = mock(Gmail.Users.Messages.List.class);
        mockMessagesGet = mock(Gmail.Users.Messages.Get.class);
        mockAttachments = mockMessages.attachments();
        mockAttachmentsGet = mock(Gmail.Users.Messages.Attachments.Get.class);
        mockWatchCall = mock(Gmail.Users.Watch.class);

        when(clientProvider.getClient(any())).thenReturn(mockGmail);
    }

    @Test
    void syncConnection_retrievesMetadata_extractsHeadersAndAttachments_withDeduplication() throws IOException {
        String msgId = "test-msg-001";

        // Mock messages.list
        when(mockMessages.list("me")).thenReturn(mockMessagesList);
        when(mockMessagesList.setMaxResults(any())).thenReturn(mockMessagesList);
        ListMessagesResponse listResponse = new ListMessagesResponse()
                .setMessages(List.of(new Message().setId(msgId)));
        when(mockMessagesList.execute()).thenReturn(listResponse);

        // Mock users.getProfile
        Profile mockProfile = new Profile().setHistoryId(BigInteger.valueOf(500123L));
        when(mockUsers.getProfile("me").execute()).thenReturn(mockProfile);

        // Mock messages.get
        Message sampleMessage = new Message()
                .setId(msgId)
                .setThreadId("th-001")
                .setHistoryId(BigInteger.valueOf(500123L))
                .setInternalDate(System.currentTimeMillis())
                .setPayload(new MessagePart()
                        .setHeaders(List.of(
                                new MessagePartHeader().setName("Subject").setValue("Confidential Financial Review"),
                                new MessagePartHeader().setName("From").setValue("cfo@workspace.com"),
                                new MessagePartHeader().setName("To").setValue("audited.employee@workspace.com")
                        ))
                        .setParts(List.of(
                                new MessagePart()
                                        .setFilename("financial_statement.pdf")
                                        .setMimeType("application/pdf")
                                        .setBody(new MessagePartBody().setAttachmentId("att-part-1").setSize(10240))
                        ))
                );

        when(mockMessages.get("me", msgId)).thenReturn(mockMessagesGet);
        when(mockMessagesGet.setFormat("full")).thenReturn(mockMessagesGet);
        when(mockMessagesGet.execute()).thenReturn(sampleMessage);

        // 1. First sync run
        GmailSyncResultDto result = syncService.syncConnection(connection.getId());

        assertThat(result.messagesProcessed()).isEqualTo(1);
        assertThat(result.attachmentsIdentified()).isEqualTo(1);

        var savedMsg = messageRecordRepository.findByConnectionIdAndMessageId(connection.getId(), msgId);
        assertThat(savedMsg).isPresent();
        assertThat(savedMsg.get().getSubject()).isEqualTo("Confidential Financial Review");
        assertThat(savedMsg.get().getSender()).isEqualTo("cfo@workspace.com");
        assertThat(savedMsg.get().isHasAttachments()).isTrue();
        assertThat(savedMsg.get().getAttachmentCount()).isEqualTo(1);

        var attachments = attachmentRecordRepository.findByMessageRecordId(savedMsg.get().getId());
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getFilename()).isEqualTo("financial_statement.pdf");
        assertThat(attachments.get(0).getDeclaredMimeType()).isEqualTo("application/pdf");
        assertThat(attachments.get(0).isDownloaded()).isFalse();

        // 2. Second sync run (deduplication test - should skip existing message)
        GmailSyncResultDto secondRun = syncService.syncConnection(connection.getId());
        assertThat(secondRun.messagesProcessed()).isEqualTo(0);
    }

    @Test
    void downloadAndRegisterAttachment_computesSha256_detectsMime_andRegistersDocument() throws IOException {
        // Prepare message and attachment in database
        GmailMessageRecord msgRecord = new GmailMessageRecord();
        msgRecord.setConnection(connection);
        msgRecord.setMessageId("msg-to-download-002");
        msgRecord.setGoogleEmail(connection.getAccountIdentifier());
        msgRecord.setSubject("Vendor Contract Review");
        msgRecord.setSender("legal@vendor.com");
        msgRecord.setProcessedAt(Instant.now());
        msgRecord = messageRecordRepository.save(msgRecord);

        GmailAttachmentRecord attRecord = new GmailAttachmentRecord();
        attRecord.setMessageRecord(msgRecord);
        attRecord.setAttachmentId("att-download-xyz");
        attRecord.setFilename("contract_agreement.txt");
        attRecord.setDeclaredMimeType("text/plain");
        attRecord.setFileSizeBytes(25L);
        attRecord.setDownloaded(false);
        attRecord = attachmentRecordRepository.save(attRecord);

        // Mock attachment content from Google
        byte[] sampleBytes = "Confidential agreement contract bytes".getBytes(StandardCharsets.UTF_8);
        String base64UrlData = Base64.getUrlEncoder().encodeToString(sampleBytes);

        when(mockAttachments.get("me", msgRecord.getMessageId(), attRecord.getAttachmentId())).thenReturn(mockAttachmentsGet);
        when(mockAttachmentsGet.execute()).thenReturn(new MessagePartBody().setData(base64UrlData));

        // Execute download & ingestion
        DocumentUploadResult uploadResult = syncService.downloadAndRegisterAttachment(attRecord.getId(), null);

        assertThat(uploadResult).isNotNull();
        assertThat(uploadResult.documentId()).isNotNull();
        assertThat(uploadResult.sha256Hash()).hasSize(64);

        // Verify attachment record was updated
        GmailAttachmentRecord updatedAtt = attachmentRecordRepository.findById(attRecord.getId()).orElseThrow();
        assertThat(updatedAtt.isDownloaded()).isTrue();
        assertThat(updatedAtt.getDocumentId()).isEqualTo(uploadResult.documentId());
        assertThat(updatedAtt.getSha256Hash()).isEqualTo(uploadResult.sha256Hash());
        assertThat(updatedAtt.getDownloadedAt()).isNotNull();

        // Verify document is registered in Document Registry
        Document savedDoc = documentRepository.findById(uploadResult.documentId()).orElseThrow();
        assertThat(savedDoc.getTitle()).isEqualTo("contract_agreement.txt");
        assertThat(savedDoc.getSourceDocumentId()).contains(msgRecord.getMessageId());

        // Verify DocumentEvent audit record
        var events = documentEventRepository.findByDocumentId(uploadResult.documentId(), org.springframework.data.domain.Pageable.unpaged());
        assertThat(events.getContent()).anyMatch(e -> e.getEventType().equals("GMAIL_ATTACHMENT_INGESTED"));
    }

    @Test
    void registerWatch_and_handlePubSubNotification_successfullyTriggersSync() throws IOException {
        // Mock watch response from Google
        WatchResponse mockResponse = new WatchResponse()
                .setHistoryId(BigInteger.valueOf(777666L))
                .setExpiration(System.currentTimeMillis() + (7L * 86400 * 1000));

        when(mockUsers.watch(eq("me"), any())).thenReturn(mockWatchCall);
        when(mockWatchCall.execute()).thenReturn(mockResponse);

        // 1. Register watch
        GmailWatch watch = watchService.registerWatch(connection.getId());

        assertThat(watch).isNotNull();
        assertThat(watch.getGoogleEmail()).isEqualTo(connection.getAccountIdentifier());
        assertThat(watch.getHistoryId()).isEqualTo(777666L);
        assertThat(watch.isActive()).isTrue();

        // 2. Receive Pub/Sub notification for this email
        String pubSubJson = String.format("{\"emailAddress\":\"%s\",\"historyId\":777670}", connection.getAccountIdentifier());
        String encodedData = Base64.getEncoder().encodeToString(pubSubJson.getBytes(StandardCharsets.UTF_8));

        PubSubNotificationRequest notification = new PubSubNotificationRequest(
                new PubSubNotificationRequest.PubSubMessage(encodedData, null, "pubsub-msg-123", "2026-11-20T16:00:00Z"),
                "projects/test-project/subscriptions/test-sub"
        );

        // Mock empty messages for subsequent sync trigger
        when(mockMessages.list("me")).thenReturn(mockMessagesList);
        when(mockMessagesList.setMaxResults(any())).thenReturn(mockMessagesList);
        when(mockMessagesList.execute()).thenReturn(new ListMessagesResponse());
        when(mockUsers.getProfile("me").execute()).thenReturn(new Profile().setHistoryId(BigInteger.valueOf(777670L)));

        watchService.handlePubSubNotification(notification);

        // Verify watch is active in DB
        var activeWatch = watchRepository.findByGoogleEmailAndActiveTrue(connection.getAccountIdentifier());
        assertThat(activeWatch).isPresent();
    }
}
