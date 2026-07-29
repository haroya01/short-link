package com.example.short_link.event.infrastructure.persistence;

import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/** 계정 하드 삭제 시 이벤트와 하위 데이터 전부 제거 — FK 에 ON DELETE CASCADE 없음. */
@Repository
class EventUserDataEraser implements UserDataEraser {

  @PersistenceContext private EntityManager em;

  @Override
  public void eraseFor(long userId) {
    em.createNativeQuery(
            "DELETE er FROM event_registration er JOIN event e ON er.event_id = e.id "
                + "WHERE e.user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery(
            "DELETE eq FROM event_question eq JOIN event e ON eq.event_id = e.id "
                + "WHERE e.user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery(
            "DELETE el FROM event_link el JOIN event e ON el.event_id = e.id "
                + "WHERE e.user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM event WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
  }
}
