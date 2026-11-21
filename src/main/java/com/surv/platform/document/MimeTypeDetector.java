package com.surv.platform.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Detects MIME types via Apache Tika magic-byte (content) inspection.
 * NEVER trusts file extensions or declared Content-Type headers alone.
 * A file named "report.pdf" may contain executable code — always inspect bytes.
 */
@Slf4j
@Component
public class MimeTypeDetector {

    private static final Set<String> BLOCKED_MIME_TYPES = Set.of(
            "application/x-msdownload",
            "application/x-dosexec",
            "application/x-executable",
            "application/x-elf",
            "application/x-mach-binary",
            "application/x-sh",
            "application/x-bat",
            "application/x-csh",
            "text/x-sh",
            "application/x-msdos-program"
    );

    private final Tika tika = new Tika();

    /**
     * Detect MIME type from raw bytes. Always inspects content — ignores extension.
     *
     * @param content  raw file bytes
     * @param filename original filename (used only as a tiebreaker hint, never trusted alone)
     * @return detected MIME type string
     */
    public String detect(byte[] content, String filename) {
        try {
            String detectedType = tika.detect(content, filename);
            log.debug("MIME detection: filename='{}' detected='{}'", filename, detectedType);
            return detectedType;
        } catch (Exception e) {
            log.warn("MIME detection failed for '{}': {}", filename, e.getMessage());
            return "application/octet-stream";
        }
    }

    /**
     * Detect MIME type from an InputStream.
     */
    public String detect(InputStream inputStream, String filename) {
        try {
            return tika.detect(inputStream, filename);
        } catch (IOException e) {
            log.warn("MIME stream detection failed for '{}': {}", filename, e.getMessage());
            return "application/octet-stream";
        }
    }

    /**
     * Returns true if the content-detected MIME type is safe to store and process.
     * Blocks executables, scripts, and OS-specific binaries regardless of extension.
     */
    public boolean isSafeContentType(String detectedMimeType) {
        if (detectedMimeType == null) return false;
        return !BLOCKED_MIME_TYPES.contains(detectedMimeType) &&
               !detectedMimeType.contains("executable") &&
               !detectedMimeType.contains("x-sh") &&
               !detectedMimeType.contains("x-bat");
    }
}
