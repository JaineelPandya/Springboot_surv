package com.surv.platform.integration.gmail.security;

import com.surv.platform.config.GmailProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialEncryptionServiceTest {

    private CredentialEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        GmailProperties properties = new GmailProperties();
        properties.setEncryptionKey("test-aes-key-for-unit-tests-012345");
        encryptionService = new CredentialEncryptionService(properties);
    }

    @Test
    void encryptAndDecrypt_returnsOriginalPlaintext() {
        String token = "ya29.a0AfH6SMBx_sample_google_access_token_12345";
        String encrypted = encryptionService.encrypt(token);

        assertThat(encrypted).isNotNull().isNotEqualTo(token);

        String decrypted = encryptionService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(token);
    }

    @Test
    void encrypt_usesRandomIv_producingDistinctCiphertextForSamePlaintext() {
        String token = "1//04sample_refresh_token_secret_xyz";
        String encrypted1 = encryptionService.encrypt(token);
        String encrypted2 = encryptionService.encrypt(token);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(token);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(token);
    }

    @Test
    void decrypt_withInvalidPayload_throwsException() {
        assertThatThrownBy(() -> encryptionService.decrypt("not-valid-base64!!"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullHandling() {
        assertThat(encryptionService.encrypt(null)).isNull();
        assertThat(encryptionService.decrypt(null)).isNull();
    }
}
