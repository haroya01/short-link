package com.example.short_link.notification.application.read;

import com.example.short_link.notification.application.NotificationTargetCodec;
import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.dto.NotificationListResult;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
import com.example.short_link.notification.application.dto.NotificationTarget;
import com.example.short_link.notification.application.dto.NotificationView;
import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationActorReader;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

  private static final int MAX_LIMIT = 50;

  private final NotificationRepository repository;
  private final NotificationActorReader actorReader;
  private final NotificationTargetCodec targetCodec;

  @Transactional(readOnly = true)
  public NotificationListResult list(Long recipientUserId, Long beforeId, int limit) {
    int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
    // Over-fetch by one to learn whether a further page exists without a second count query.
    List<NotificationEntity> rows =
        repository.findPageForRecipient(recipientUserId, beforeId, capped + 1);
    boolean hasMore = rows.size() > capped;
    List<NotificationEntity> page = hasMore ? rows.subList(0, capped) : rows;

    Map<Long, NotificationActor> actors =
        actorReader.resolve(
            page.stream()
                .map(NotificationEntity::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

    List<NotificationView> items = page.stream().map(row -> toView(row, actors)).toList();
    Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
    return new NotificationListResult(items, nextCursor, hasMore);
  }

  @Transactional(readOnly = true)
  public long unreadCount(Long recipientUserId) {
    return repository.countUnread(recipientUserId);
  }

  private NotificationView toView(NotificationEntity row, Map<Long, NotificationActor> actors) {
    NotificationActor actor =
        row.getActorUserId() == null ? null : actors.get(row.getActorUserId());
    NotificationType type = row.getType();
    NotificationTarget target = targetCodec.decode(type, row.getPayload());
    NotificationPostRef post = target instanceof NotificationPostRef ref ? ref : null;
    NotificationSeriesRef series = target instanceof NotificationSeriesRef ref ? ref : null;
    NotificationCollectionRef collection =
        target instanceof NotificationCollectionRef ref ? ref : null;
    return new NotificationView(
        row.getId(), type, actor, post, series, collection, row.isRead(), row.getCreatedAt());
  }
}
