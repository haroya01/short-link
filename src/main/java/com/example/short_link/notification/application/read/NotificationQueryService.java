package com.example.short_link.notification.application.read;

import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.dto.NotificationListResult;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
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
import tools.jackson.databind.json.JsonMapper;

/** Reads a recipient's notification feed and unread count, resolving actor identity per page. */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

  private static final int MAX_LIMIT = 50;

  private final NotificationRepository repository;
  private final NotificationActorReader actorReader;
  private final JsonMapper jsonMapper;

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
    // The payload shape follows the type: a series ref for SERIES_SUBSCRIBE, a collection ref for
    // the graph notices (CONNECTED / PATH_GREW), else a post ref (LIKE/COMMENT/REPLY/NEW_POST; null
    // for FOLLOW). Only the matching field is decoded so the others stay null.
    NotificationType type = row.getType();
    NotificationSeriesRef series =
        type == NotificationType.SERIES_SUBSCRIBE
            ? decode(row.getPayload(), NotificationSeriesRef.class)
            : null;
    NotificationCollectionRef collection =
        isCollectionType(type) ? decode(row.getPayload(), NotificationCollectionRef.class) : null;
    NotificationPostRef post =
        series == null && collection == null
            ? decode(row.getPayload(), NotificationPostRef.class)
            : null;
    return new NotificationView(
        row.getId(), type, actor, post, series, collection, row.isRead(), row.getCreatedAt());
  }

  private static boolean isCollectionType(NotificationType type) {
    return type == NotificationType.CONNECTED || type == NotificationType.PATH_GREW;
  }

  private <T> T decode(String payload, Class<T> type) {
    if (payload == null || payload.isBlank()) {
      return null;
    }
    return jsonMapper.readValue(payload, type);
  }
}
