package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.repository.FederationUserReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class FederationUserReaderAdapter implements FederationUserReader {

  private static final String SELECT =
      "SELECT id, username, bio, avatar_url FROM users WHERE deleted_at IS NULL AND ";

  @PersistenceContext private EntityManager em;

  @Override
  public Optional<FederationUser> findActiveByUsername(String username) {
    return first(
        em.createNativeQuery(SELECT + "username = :username")
            .setParameter("username", username)
            .getResultList());
  }

  @Override
  public Optional<FederationUser> findActiveById(Long userId) {
    return first(
        em.createNativeQuery(SELECT + "id = :id AND username IS NOT NULL")
            .setParameter("id", userId)
            .getResultList());
  }

  private static Optional<FederationUser> first(List<?> rows) {
    return rows.stream()
        .findFirst()
        .map(
            raw -> {
              Object[] columns = (Object[]) raw;
              return new FederationUser(
                  ((Number) columns[0]).longValue(),
                  (String) columns[1],
                  (String) columns[2],
                  (String) columns[3]);
            });
  }
}
