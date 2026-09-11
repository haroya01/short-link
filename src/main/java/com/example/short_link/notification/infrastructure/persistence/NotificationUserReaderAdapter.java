package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.NotificationUser;
import com.example.short_link.notification.domain.repository.NotificationUserReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Reads only push identity and locale; preserves the former user repository's inclusion rules. */
@Repository
class NotificationUserReaderAdapter implements NotificationUserReader {
  @PersistenceContext private EntityManager em;

  @Override
  public Optional<NotificationUser> findById(Long userId) {
    Objects.requireNonNull(userId, "userId");
    return findAllByIdIn(List.of(userId)).stream().findFirst();
  }

  @Override
  public List<NotificationUser> findAllByIdIn(Collection<Long> userIds) {
    if (userIds.isEmpty()) return List.of();
    List<?> rows =
        em.createNativeQuery("SELECT id, username, locale FROM users WHERE id IN (:ids)")
            .setParameter("ids", userIds)
            .getResultList();
    return rows.stream()
        .map(
            raw -> {
              Object[] columns = (Object[]) raw;
              return new NotificationUser(
                  ((Number) columns[0]).longValue(), (String) columns[1], (String) columns[2]);
            })
        .toList();
  }
}
