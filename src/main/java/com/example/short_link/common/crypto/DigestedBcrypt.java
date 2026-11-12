package com.example.short_link.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// BCrypt reads at most 72 bytes; a 200-character Korean password is about 600, so it is digested
// first.
public final class DigestedBcrypt implements PasswordEncoder {

  private final BCryptPasswordEncoder bcrypt;

  public DigestedBcrypt(int strength) {
    this.bcrypt = new BCryptPasswordEncoder(strength);
  }

  @Override
  public String encode(CharSequence raw) {
    return bcrypt.encode(digest(raw));
  }

  @Override
  public boolean matches(CharSequence raw, String hash) {
    return raw != null && hash != null && bcrypt.matches(digest(raw), hash);
  }

  private static String digest(CharSequence raw) {
    try {
      byte[] sha =
          MessageDigest.getInstance("SHA-256")
              .digest(raw.toString().getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(sha);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
