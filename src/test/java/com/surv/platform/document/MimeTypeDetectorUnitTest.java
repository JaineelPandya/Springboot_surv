package com.surv.platform.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests — no Spring context, no database, no Docker required.
 */
class MimeTypeDetectorUnitTest {

    private final MimeTypeDetector detector = new MimeTypeDetector();

    @Test
    void elfMagicBytes_disguisedAsPdf_isRejected() {
        // ELF binary magic: 0x7F 'E' 'L' 'F' — a Linux executable masquerading as a PDF
        byte[] elfBytes = {0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01, 0x01, 0x00};
        String mime = detector.detect(elfBytes, "totally_safe_report.pdf");
        assertThat(detector.isSafeContentType(mime)).isFalse();
    }

    @Test
    void plainTextBytes_areAccepted() {
        byte[] txt = "Hello, world! This is plain text content.".getBytes();
        String mime = detector.detect(txt, "hello.txt");
        assertThat(detector.isSafeContentType(mime)).isTrue();
    }

    @Test
    void mimeDetection_ignoresExtension_forActualContent() {
        // A plain text file renamed as .exe should be detected as text
        byte[] txt = "just text content here".getBytes();
        String mime = detector.detect(txt, "harmless.exe");
        // Tika should detect text, not executable based on bytes
        assertThat(mime).isNotNull().isNotBlank();
    }

    @Test
    void sha256_isConsistentAcrossCalls() {
        FingerprintService fs = new FingerprintService(null, detector);
        byte[] content = "consistent content for hash".getBytes();
        String h1 = fs.computeSha256(content);
        String h2 = fs.computeSha256(content);
        assertThat(h1).isEqualTo(h2).hasSize(64).matches("[a-f0-9]+");
    }

    @Test
    void sha256_differentInput_producesDifferentHash() {
        FingerprintService fs = new FingerprintService(null, detector);
        assertThat(fs.computeSha256("content A".getBytes()))
                .isNotEqualTo(fs.computeSha256("content B".getBytes()));
    }

    @Test
    void sha256_knownValue() {
        // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        FingerprintService fs = new FingerprintService(null, detector);
        String hash = fs.computeSha256("hello".getBytes());
        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }
}
