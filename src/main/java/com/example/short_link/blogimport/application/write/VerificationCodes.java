package com.example.short_link.blogimport.application.write;

import java.security.SecureRandom;

public final class VerificationCodes {

  private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
  private static final int LENGTH = 6;
  private static final SecureRandom RANDOM = new SecureRandom();

  private VerificationCodes() {}

  public static String next() {
    StringBuilder code = new StringBuilder("kurl-");
    for (int i = 0; i < LENGTH; i++) {
      code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }
}
