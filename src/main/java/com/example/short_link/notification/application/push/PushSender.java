package com.example.short_link.notification.application.push;

import java.util.Collection;

/** 인앱 알림과 같은 내용을 기기로도 — 구현이 미설정이면 조용한 no-op 이어야 한다. */
public interface PushSender {

  void send(Long recipientUserId, PushMessage message);

  void sendToAll(Collection<Long> recipientUserIds, PushMessage message);

  /** subtitle 은 글 제목 같은 보조 줄 — 없으면 null. */
  record PushMessage(String title, String subtitle, String body) {}
}
