package com.example.short_link.event.application.helper;

import java.security.SecureRandom;

/** 슬러그는 제목이나 PII를 포함하지 않는 무작위 값이다. */
public final class EventSlugs {

  private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
  private static final int LENGTH = 10;
  private static final SecureRandom RANDOM = new SecureRandom();

  private EventSlugs() {}

  public static String generate() {
    StringBuilder sb = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }
}
