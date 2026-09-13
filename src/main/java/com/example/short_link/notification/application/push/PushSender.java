package com.example.short_link.notification.application.push;

import java.util.Collection;

/** 푸시 전송 포트. 미설정이면 no-op이며, 커밋 후 호출 정책은 {@link NotificationPushDelivery}가 소유한다. */
public interface PushSender {

  void send(Long recipientUserId, PushMessage message);

  void sendToAll(Collection<Long> recipientUserIds, PushMessage message);

  /** subtitle은 선택 보조 문구다. type·shortCode는 기기 앱의 라우팅 힌트이며, 라우팅 대상이나 특정 링크가 없는 알림에서는 null이다. */
  record PushMessage(String title, String subtitle, String body, String type, String shortCode) {

    public PushMessage(String title, String subtitle, String body) {
      this(title, subtitle, body, null, null);
    }
  }
}
