package com.example.short_link.user.application.twofactor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AES-GCM at-rest encryption for sensitive user secrets (TOTP). Reads the master key from {@code
 * TWOFA_AES_KEY} (base64-encoded 32 bytes). If unset — only acceptable in dev — secrets are stored
 * unencrypted with a {@code plain:} prefix so we can roll over later by re-encrypting at read time.
 *
 * <p>Output format: {@code v1:<base64(iv|ciphertext|tag)>} for encrypted, {@code plain:<value>} for
 * dev fallback. The version prefix lets us migrate algorithms without losing existing rows.
 */
@Slf4j
@Component
public class SecretCipher {

  private static final String CIPHER = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final String PREFIX_V1 = "v1:";
  private static final String PREFIX_PLAIN = "plain:";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SecretKeySpec key;

  public SecretCipher(TwoFactorProperties props) {
    String base64Key = props.aesKey();
    if (base64Key.isBlank()) {
      log.warn(
          "TWOFA_AES_KEY not set — 2FA secrets will be stored unencrypted. Set the env var in"
              + " production.");
      this.key = null;
    } else {
      byte[] decoded = Base64.getDecoder().decode(base64Key);
      if (decoded.length != 32) {
        throw new IllegalStateException(
            "TWOFA_AES_KEY must decode to 32 bytes (256-bit AES) but was " + decoded.length);
      }
      this.key = new SecretKeySpec(decoded, "AES");
    }
  }

  public String encrypt(String plaintext) {
    if (key == null) return PREFIX_PLAIN + plaintext;
    try {
      byte[] iv = new byte[IV_BYTES];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(CIPHER);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
      return PREFIX_V1 + Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("AES-GCM encryption failed", e);
    }
  }

  public String decrypt(String stored) {
    if (stored == null) return null;
    if (stored.startsWith(PREFIX_PLAIN)) return stored.substring(PREFIX_PLAIN.length());
    if (!stored.startsWith(PREFIX_V1)) {
      // backwards-compat: pre-prefix rows were base32 secrets in plaintext
      return stored;
    }
    if (key == null) {
      throw new IllegalStateException(
          "Cannot decrypt v1 ciphertext — TWOFA_AES_KEY is missing on this instance");
    }
    try {
      byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX_V1.length()));
      byte[] iv = new byte[IV_BYTES];
      System.arraycopy(combined, 0, iv, 0, IV_BYTES);
      byte[] ct = new byte[combined.length - IV_BYTES];
      System.arraycopy(combined, IV_BYTES, ct, 0, ct.length);
      Cipher cipher = Cipher.getInstance(CIPHER);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("AES-GCM decryption failed", e);
    }
  }
}
