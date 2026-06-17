package com.example.short_link.notification.infrastructure;

import static org.mockito.Mockito.verify;

import com.example.short_link.notification.application.push.PushSender;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompositePushSenderTest {

  @Mock private ApnsPushSender apns;
  @Mock private WebPushSender web;

  @Test
  void fansSendToBothChannels() {
    CompositePushSender composite = new CompositePushSender(apns, web);
    PushSender.PushMessage message = new PushSender.PushMessage("kurl", "글 제목", "새 글");

    composite.send(7L, message);

    verify(apns).send(7L, message);
    verify(web).send(7L, message);
  }

  @Test
  void fansSendToAllToBothChannels() {
    CompositePushSender composite = new CompositePushSender(apns, web);
    PushSender.PushMessage message = new PushSender.PushMessage("kurl", "글 제목", "새 글");
    List<Long> recipients = List.of(1L, 2L, 3L);

    composite.sendToAll(recipients, message);

    verify(apns).sendToAll(recipients, message);
    verify(web).sendToAll(recipients, message);
  }
}
