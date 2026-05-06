package com.example.short_link.link.application;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

  private static final String ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final int LENGTH = 7;

  private final SecureRandom random = new SecureRandom();

  public String generate() {
    StringBuilder sb = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }
}
