package com.example.short_link.link.application.write;

import java.time.Instant;

/**
 * deduplicate=true 면 같은 owner + 같은 URL 의 기존 link 재사용 (일반 단축 흐름). Campaign batch 가 같은 destination 으로
 * 여러 묶음을 만들 때 batch:link UNIQUE 제약과 충돌하므로 false 로 호출 — 각 batch 가 자기 단축 코드를 갖도록 한다.
 */
public record CreateLinkCommand(
    String url, Long userId, String customCode, Instant expiresAt, boolean deduplicate) {

  public CreateLinkCommand {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("url required");
    }
  }

  public static CreateLinkCommand of(
      String url, Long userId, String customCode, Instant expiresAt) {
    return new CreateLinkCommand(url, userId, customCode, expiresAt, true);
  }
}
