package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * Purges the notification slice's user-owned rows on account hard delete — none of these FKs carry
 * ON DELETE CASCADE. Covers the blog interaction inbox ({@code notification}, both as recipient and
 * as the actor referenced from other inboxes), the link-owner notification feed ({@code
 * link_notification}), and both per-type opt-out tables ({@code notification_preference} for link
 * alerts, {@code blog_notification_preference} for the blog bell).
 */
@Repository
class NotificationUserDataEraser implements UserDataEraser {

  @PersistenceContext private EntityManager em;

  @Override
  public void eraseFor(long userId) {
    em.createNativeQuery(
            "DELETE FROM notification WHERE recipient_user_id = :userId"
                + " OR actor_user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM link_notification WHERE recipient_user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM notification_preference WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    // 블로그 벨 옵트아웃(V108)은 users FK 가 아예 없어 cascade 로도 안 지워진다 — 여기서 안 비우면
    // 계정 영구삭제 뒤 고아 행이 남아 방침의 삭제 약속(§8)과 어긋난다(2026-07-15 감사에서 발견).
    em.createNativeQuery("DELETE FROM blog_notification_preference WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
  }
}
