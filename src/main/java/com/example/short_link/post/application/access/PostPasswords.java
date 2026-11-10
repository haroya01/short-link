package com.example.short_link.post.application.access;

import com.example.short_link.common.crypto.DigestedBcrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PostPasswords {

  public static final int MIN_LENGTH = 4;
  public static final int MAX_LENGTH = 200;

  private final PasswordEncoder bcrypt = new DigestedBcrypt(10);

  public static boolean acceptableLength(String raw) {
    int length = raw.codePointCount(0, raw.length());
    return length >= MIN_LENGTH && length <= MAX_LENGTH;
  }

  public String hash(String raw) {
    return bcrypt.encode(raw);
  }

  public boolean matches(String raw, String hash) {
    return bcrypt.matches(raw, hash);
  }
}
