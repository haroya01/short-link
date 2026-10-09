package com.example.short_link.notification.application.write;

import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Each fan-out chunk uses its own transaction to release the DB connection between chunks.
@Service
@RequiredArgsConstructor
public class NotificationFanoutWriter {

  private final NotificationRepository repository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persistChunk(
      List<Long> recipientUserIds,
      NotificationType type,
      Long actorUserId,
      Long actorRemoteId,
      String json,
      Set<Long> hidden,
      Instant at) {
    for (Long recipientUserId : recipientUserIds) {
      NotificationEntity notice =
          new NotificationEntity(recipientUserId, type, actorUserId, actorRemoteId, json, null);
      if (hidden.contains(recipientUserId)) {
        notice.markRead(at);
      }
      repository.save(notice);
    }
  }
}
