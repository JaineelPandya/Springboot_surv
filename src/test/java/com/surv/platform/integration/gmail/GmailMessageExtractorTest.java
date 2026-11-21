package com.surv.platform.integration.gmail;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.surv.platform.integration.gmail.dto.EmailMetadataDto;
import com.surv.platform.integration.gmail.service.GmailMessageExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GmailMessageExtractorTest {

    private final GmailMessageExtractor extractor = new GmailMessageExtractor();

    @Test
    void extractMetadata_extractsHeadersAndAttachmentsCorrectly() {
        Message message = new Message();
        message.setId("msg-101");
        message.setThreadId("th-202");
        message.setHistoryId(BigInteger.valueOf(999888L));
        message.setInternalDate(1700000000000L);

        MessagePart payload = new MessagePart();
        payload.setHeaders(List.of(
                new MessagePartHeader().setName("Subject").setValue("Confidential Audit Report Q4"),
                new MessagePartHeader().setName("From").setValue("compliance@company.com"),
                new MessagePartHeader().setName("To").setValue("auditor@company.com, ceo@company.com"),
                new MessagePartHeader().setName("Cc").setValue("legal@company.com"),
                new MessagePartHeader().setName("Bcc").setValue("archive@company.com")
        ));

        // Part 1: body text (should not be treated as attachment)
        MessagePart textPart = new MessagePart();
        textPart.setMimeType("text/plain");
        textPart.setFilename("");
        textPart.setBody(new MessagePartBody().setSize(150));

        // Part 2: PDF attachment
        MessagePart attPart = new MessagePart();
        attPart.setFilename("q4_confidential.pdf");
        attPart.setMimeType("application/pdf");
        attPart.setBody(new MessagePartBody().setAttachmentId("att-xyz-987").setSize(45000));

        payload.setParts(List.of(textPart, attPart));
        message.setPayload(payload);

        EmailMetadataDto dto = extractor.extractMetadata(message);

        assertThat(dto).isNotNull();
        assertThat(dto.messageId()).isEqualTo("msg-101");
        assertThat(dto.threadId()).isEqualTo("th-202");
        assertThat(dto.historyId()).isEqualTo(999888L);
        assertThat(dto.subject()).isEqualTo("Confidential Audit Report Q4");
        assertThat(dto.sender()).isEqualTo("compliance@company.com");
        assertThat(dto.recipientsTo()).containsExactly("auditor@company.com", "ceo@company.com");
        assertThat(dto.recipientsCc()).containsExactly("legal@company.com");
        assertThat(dto.recipientsBcc()).containsExactly("archive@company.com");
        assertThat(dto.receivedAt()).isNotNull();

        assertThat(dto.attachments()).hasSize(1);
        assertThat(dto.attachments().get(0).attachmentId()).isEqualTo("att-xyz-987");
        assertThat(dto.attachments().get(0).filename()).isEqualTo("q4_confidential.pdf");
        assertThat(dto.attachments().get(0).declaredMimeType()).isEqualTo("application/pdf");
        assertThat(dto.attachments().get(0).sizeBytes()).isEqualTo(45000L);
    }
}
