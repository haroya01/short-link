package com.example.short_link.post.application.access;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

// BCrypt reads at most 72 bytes; a 200-character Korean password is about 600, so it is digested
// first.
@Component
public class PostPasswords {

  public static final int MIN_LENGTH = 4;
  public static final int MAX_LENGTH = 200;

  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);

  public static boolean acceptableLength(String raw) {
    int length = raw.codePointCount(0, raw.length());
    return length >= MIN_LENGTH && length <= MAX_LENGTH;
  }

  public String hash(String raw) {
    return bcrypt.encode(digest(raw));
  }

  public boolean matches(String raw, String hash) {
    return raw != null && hash != null && bcrypt.matches(digest(raw), hash);
  }

  private static String digest(String raw) {
    try {
      byte[] sha =
          MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(sha);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
