package com.example.short_link.notification.application.push;

import java.util.Collection;

/** 인앱 알림과 같은 내용을 기기로도 — 구현이 미설정이면 조용한 no-op 이어야 한다. */
public interface PushSender {

  void send(Long recipientUserId, PushMessage message);

  void sendToAll(Collection<Long> recipientUserIds, PushMessage message);

  /**
   * subtitle 은 글 제목 같은 보조 줄 — 없으면 null. {@code type}·{@code shortCode} 는 기기 앱의 라우팅 힌트다: {@code
   * type} 은 알림 종류 문자열(링크 알림이면 항상 채워지고, 라우팅이 없는 블로그 벨 등은 null), {@code shortCode} 는 링크 단위 알림이면 그
   * 코드(다이제스트처럼 링크 단위가 아니면 null).
   */
  record PushMessage(String title, String subtitle, String body, String type, String shortCode) {

    /** 라우팅 힌트가 없는 알림(블로그 벨 등) — 기기 앱은 type 없이 기본 처리한다. */
    public PushMessage(String title, String subtitle, String body) {
      this(title, subtitle, body, null, null);
    }
  }
}
