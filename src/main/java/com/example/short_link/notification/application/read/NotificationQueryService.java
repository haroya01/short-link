package com.example.short_link.notification.application.read;

import com.example.short_link.notification.application.NotificationTargetCodec;
import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.dto.NotificationListResult;
import com.example.short_link.notification.application.dto.NotificationNoteRef;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
import com.example.short_link.notification.application.dto.NotificationTarget;
import com.example.short_link.notification.application.dto.NotificationView;
import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationGroup;
import com.example.short_link.notification.domain.NotificationGroupActor;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationActorReader;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

  private static final int MAX_LIMIT = 50;
  private static final int ACTORS_PER_GROUP = 3;

  private final NotificationRepository repository;
  private final NotificationActorReader actorReader;
  private final NotificationTargetCodec targetCodec;

  @Transactional(readOnly = true)
  public NotificationListResult list(Long recipientUserId, Long beforeId, int limit) {
    int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
    // Over-fetch by one to learn whether a further page exists without a second count query.
    List<NotificationGroup> groups =
        repository.findGroupPage(recipientUserId, beforeId, capped + 1);
    boolean hasMore = groups.size() > capped;
    List<NotificationGroup> page = hasMore ? groups.subList(0, capped) : groups;

    List<String> grouped =
        page.stream()
            .filter(group -> group.newest().getGroupKey() != null && group.count() > 1)
            .map(group -> group.newest().getGroupKey())
            .toList();
    Map<String, List<NotificationGroupActor>> groupActors = new LinkedHashMap<>();
    for (NotificationGroupActor actor :
        repository.recentActors(recipientUserId, grouped, ACTORS_PER_GROUP)) {
      groupActors.computeIfAbsent(actor.groupKey(), key -> new ArrayList<>()).add(actor);
    }

    Set<Long> userIds = new HashSet<>();
    Set<Long> remoteIds = new HashSet<>();
    page.stream()
        .map(NotificationGroup::newest)
        .forEach(row -> collect(row.getActorUserId(), row.getActorRemoteId(), userIds, remoteIds));
    groupActors.values().stream()
        .flatMap(List::stream)
        .forEach(actor -> collect(actor.actorUserId(), actor.actorRemoteId(), userIds, remoteIds));
    Map<Long, NotificationActor> members = actorReader.resolve(userIds);
    Map<Long, NotificationActor> remotes =
        remoteIds.isEmpty() ? Map.of() : actorReader.resolveRemote(remoteIds);

    List<NotificationView> items = new ArrayList<>(page.size());
    for (NotificationGroup group : page) {
      NotificationEntity row = group.newest();
      NotificationActor actor =
          actorOf(row.getActorUserId(), row.getActorRemoteId(), members, remotes);
      List<NotificationActor> actors =
          row.getGroupKey() == null || group.count() <= 1
              ? (actor == null ? List.of() : List.of(actor))
              : groupActors.getOrDefault(row.getGroupKey(), List.of()).stream()
                  .map(each -> actorOf(each.actorUserId(), each.actorRemoteId(), members, remotes))
                  .filter(Objects::nonNull)
                  .toList();
      items.add(toView(row, actor, group, actors));
    }
    Long nextCursor = hasMore ? page.get(page.size() - 1).newest().getId() : null;
    return new NotificationListResult(items, nextCursor, hasMore);
  }

  @Transactional(readOnly = true)
  public long unreadCount(Long recipientUserId) {
    return repository.countUnread(recipientUserId);
  }

  private static void collect(Long userId, Long remoteId, Set<Long> userIds, Set<Long> remoteIds) {
    if (userId != null) {
      userIds.add(userId);
    }
    if (remoteId != null) {
      remoteIds.add(remoteId);
    }
  }

  private static NotificationActor actorOf(
      Long userId,
      Long remoteId,
      Map<Long, NotificationActor> members,
      Map<Long, NotificationActor> remotes) {
    if (userId != null) {
      return members.get(userId);
    }
    return remoteId == null ? null : remotes.get(remoteId);
  }

  private NotificationView toView(
      NotificationEntity row,
      NotificationActor actor,
      NotificationGroup group,
      List<NotificationActor> actors) {
    NotificationType type = row.getType();
    NotificationTarget target = targetCodec.decode(type, row.getPayload());
    NotificationPostRef post = target instanceof NotificationPostRef ref ? ref : null;
    NotificationSeriesRef series = target instanceof NotificationSeriesRef ref ? ref : null;
    NotificationCollectionRef collection =
        target instanceof NotificationCollectionRef ref ? ref : null;
    NotificationNoteRef note = target instanceof NotificationNoteRef ref ? ref : null;
    return new NotificationView(
        row.getId(),
        type,
        actor,
        post,
        series,
        collection,
        note,
        group.count(),
        actors,
        !group.unread(),
        row.getCreatedAt());
  }
}
