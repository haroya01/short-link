package com.example.short_link.notification.infrastructure;

import com.example.short_link.notification.application.push.PushSender;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 한 알림을 모든 푸시 채널로 — APNs(iOS 앱)와 웹푸시(브라우저)에 동시에 보낸다. {@link
 * com.example.short_link.notification.application.write.RecordBlogNotificationUseCase} 가 주입받는 단일
 * 발송기(@Primary). 각 채널은 미설정이면 스스로 no-op 이라 여기선 분기 없이 둘 다 호출만 한다. 델리게이트는 구체 타입으로 받아(자기 자신 재주입 방지) 한
 * 번씩만 부른다.
 */
@Component
@Primary
public class CompositePushSender implements PushSender {

  private final List<PushSender> delegates;

  public CompositePushSender(ApnsPushSender apns, WebPushSender web) {
    this.delegates = List.of(apns, web);
  }

  @Override
  public void send(Long recipientUserId, PushMessage message) {
    delegates.forEach(d -> d.send(recipientUserId, message));
  }

  @Override
  public void sendToAll(Collection<Long> recipientUserIds, PushMessage message) {
    delegates.forEach(d -> d.sendToAll(recipientUserIds, message));
  }
}
