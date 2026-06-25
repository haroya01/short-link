package com.example.short_link.notification.application.read;

import com.example.short_link.notification.application.dto.LinkNotificationListResult;
import com.example.short_link.notification.application.dto.LinkNotificationView;
import com.example.short_link.notification.domain.LinkNotificationEntity;
import com.example.short_link.notification.domain.repository.LinkNotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 링크 알림 인박스 조회 — 커서 페이지네이션 + 안 읽음 카운트. 블로그 NotificationQueryService 와 같은 결. */
@Service
@RequiredArgsConstructor
public class LinkNotificationQueryService {
  private static final int MAX_LIMIT = 50;

  private final LinkNotificationRepository repository;

  @Transactional(readOnly = true)
  public LinkNotificationListResult list(Long recipientUserId, Long beforeId, int limit) {
    int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
    List<LinkNotificationEntity> rows =
        repository.findPageForRecipient(recipientUserId, beforeId, capped + 1);
    boolean hasMore = rows.size() > capped;
    List<LinkNotificationEntity> page = hasMore ? rows.subList(0, capped) : rows;
    List<LinkNotificationView> items = page.stream().map(this::toView).toList();
    Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
    return new LinkNotificationListResult(items, nextCursor, hasMore);
  }

  @Transactional(readOnly = true)
  public long unreadCount(Long recipientUserId) {
    return repository.countUnread(recipientUserId);
  }

  private LinkNotificationView toView(LinkNotificationEntity row) {
    return new LinkNotificationView(
        row.getId(),
        row.getType(),
        row.getShortCode(),
        row.getSubtitle(),
        row.getBody(),
        row.isRead(),
        row.getCreatedAt());
  }
}
