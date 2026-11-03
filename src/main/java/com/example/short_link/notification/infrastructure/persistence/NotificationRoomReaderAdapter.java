package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.repository.NotificationRoomReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class NotificationRoomReaderAdapter implements NotificationRoomReader {

  @PersistenceContext private EntityManager em;

  @Override
  public Optional<String> name(Long roomId) {
    List<?> rows =
        em.createNativeQuery("SELECT name FROM room WHERE id = :id")
            .setParameter("id", roomId)
            .getResultList();
    return rows.stream().findFirst().map(Object::toString);
  }
}
