package com.example.short_link.event.application.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/** 계정 없는 참가자의 신청 취소 자격증명. 원본 토큰은 완료 화면/확인 메일에만 존재하고 DB 에는 SHA-256 만 저장 — DB 유출로 남의 신청을 취소할 수 없다. */
public final class CancelTokens {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final HexFormat HEX = HexFormat.of();

  private CancelTokens() {}

  public static String generate() {
    byte[] buf = new byte[16];
    RANDOM.nextBytes(buf);
    return HEX.formatHex(buf);
  }

  public static String hash(String token) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HEX.formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
