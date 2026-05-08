package com.example.short_link.user.application.twofactor;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 30s step, 6 digits). The secret is stored as Base32 — Google
 * Authenticator and other apps consume that format directly via the {@code otpauth://totp/} URI.
 *
 * <p>Verification accepts the previous, current, and next step to tolerate small clock drift on the
 * user's phone (RFC suggests ±1 step).
 */
public final class TotpCodec {

  public static final int CODE_DIGITS = 6;
  public static final int PERIOD_SECONDS = 30;
  private static final int SECRET_BYTES = 20;
  private static final int VERIFY_WINDOW_STEPS = 1;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

  private TotpCodec() {}

  public static String generateBase32Secret() {
    byte[] buf = new byte[SECRET_BYTES];
    RANDOM.nextBytes(buf);
    return base32Encode(buf);
  }

  public static String generateCode(String base32Secret, long timeStep) {
    byte[] key = base32Decode(base32Secret);
    byte[] msg = ByteBuffer.allocate(8).putLong(timeStep).array();
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      byte[] hash = mac.doFinal(msg);
      int offset = hash[hash.length - 1] & 0x0F;
      int binary =
          ((hash[offset] & 0x7F) << 24)
              | ((hash[offset + 1] & 0xFF) << 16)
              | ((hash[offset + 2] & 0xFF) << 8)
              | (hash[offset + 3] & 0xFF);
      int code = binary % 1_000_000;
      return String.format("%06d", code);
    } catch (Exception e) {
      throw new IllegalStateException("HMAC-SHA1 unavailable", e);
    }
  }

  public static boolean verify(String base32Secret, String code, long currentEpochSecond) {
    if (code == null || code.length() != CODE_DIGITS) return false;
    String trimmed = code.trim();
    long step = currentEpochSecond / PERIOD_SECONDS;
    for (int delta = -VERIFY_WINDOW_STEPS; delta <= VERIFY_WINDOW_STEPS; delta++) {
      if (constantTimeEquals(generateCode(base32Secret, step + delta), trimmed)) return true;
    }
    return false;
  }

  public static String provisioningUri(String issuer, String accountName, String base32Secret) {
    String label = encode(issuer) + ":" + encode(accountName);
    return "otpauth://totp/"
        + label
        + "?secret="
        + base32Secret
        + "&issuer="
        + encode(issuer)
        + "&algorithm=SHA1&digits="
        + CODE_DIGITS
        + "&period="
        + PERIOD_SECONDS;
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a.length() != b.length()) return false;
    int diff = 0;
    for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
    return diff == 0;
  }

  private static String encode(String s) {
    return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static String base32Encode(byte[] data) {
    StringBuilder out = new StringBuilder();
    int buffer = 0, bits = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xFF);
      bits += 8;
      while (bits >= 5) {
        out.append(BASE32_ALPHABET[(buffer >> (bits - 5)) & 0x1F]);
        bits -= 5;
      }
    }
    if (bits > 0) out.append(BASE32_ALPHABET[(buffer << (5 - bits)) & 0x1F]);
    return out.toString();
  }

  private static byte[] base32Decode(String s) {
    String clean = s.replace("=", "").replace(" ", "").toUpperCase();
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    int buffer = 0, bits = 0;
    for (char c : clean.toCharArray()) {
      int value;
      if (c >= 'A' && c <= 'Z') value = c - 'A';
      else if (c >= '2' && c <= '7') value = c - '2' + 26;
      else throw new IllegalArgumentException("invalid base32 character: " + c);
      buffer = (buffer << 5) | value;
      bits += 5;
      if (bits >= 8) {
        out.write((buffer >> (bits - 8)) & 0xFF);
        bits -= 8;
      }
    }
    return out.toByteArray();
  }
}
