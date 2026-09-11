package com.example.short_link.notification.infrastructure;

import com.example.short_link.notification.application.push.PushSender;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** 채널별 실패를 격리해 한 푸시 채널의 실패가 다른 채널의 전달을 막지 않게 한다. */
@Slf4j
@Component
@Primary
public class CompositePushSender implements PushSender {

  private final List<PushSender> delegates;

  public CompositePushSender(ApnsPushSender apns, WebPushSender web) {
    this.delegates = List.of(apns, web);
  }

  @Override
  public void send(Long recipientUserId, PushMessage message) {
    deliver(d -> d.send(recipientUserId, message));
  }

  @Override
  public void sendToAll(Collection<Long> recipientUserIds, PushMessage message) {
    deliver(d -> d.sendToAll(recipientUserIds, message));
  }

  private void deliver(Consumer<PushSender> delivery) {
    for (PushSender delegate : delegates) {
      try {
        delivery.accept(delegate);
      } catch (RuntimeException e) {
        log.warn("push channel {} failed: {}", delegate.getClass().getSimpleName(), e.toString());
      }
    }
  }
}
