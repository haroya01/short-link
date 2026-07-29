package com.example.short_link.event.application.helper;

import java.security.SecureRandom;

/** 이벤트 공개 URL 용 랜덤 슬러그 — 제목 기반이 아니라 추측 불가, PII 무관, 유일성 충돌 희박. */
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
