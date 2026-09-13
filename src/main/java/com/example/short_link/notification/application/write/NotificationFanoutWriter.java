package com.example.short_link.notification.application.write;

import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Each fan-out chunk uses its own transaction to release the DB connection between chunks. */
@Service
@RequiredArgsConstructor
public class NotificationFanoutWriter {

  private final NotificationRepository repository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persistChunk(
      List<Long> recipientUserIds, NotificationType type, Long actorUserId, String json) {
    for (Long recipientUserId : recipientUserIds) {
      repository.save(new NotificationEntity(recipientUserId, type, actorUserId, json));
    }
  }
}
