package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.repository.FederationUserReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
class FederationUserReaderAdapter implements FederationUserReader {

  private static final String SELECT =
      "SELECT id, username, bio, avatar_url, display_name, socials, locked FROM users"
          + " WHERE deleted_at IS NULL AND ";

  private static final TypeReference<List<FederationUser.ProfileLink>> LINKS =
      new TypeReference<>() {};

  private final JsonMapper json;

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

  private Optional<FederationUser> first(List<?> rows) {
    return rows.stream()
        .findFirst()
        .map(
            raw -> {
              Object[] columns = (Object[]) raw;
              return new FederationUser(
                  ((Number) columns[0]).longValue(),
                  (String) columns[1],
                  (String) columns[2],
                  (String) columns[3],
                  (String) columns[4],
                  links((String) columns[5]),
                  Boolean.TRUE.equals(columns[6]) || Integer.valueOf(1).equals(columns[6]));
            });
  }

  private List<FederationUser.ProfileLink> links(String socials) {
    if (socials == null || socials.isBlank()) {
      return List.of();
    }
    try {
      List<FederationUser.ProfileLink> parsed = json.readValue(socials, LINKS);
      return parsed == null
          ? List.of()
          : parsed.stream().filter(l -> l.channel() != null && l.url() != null).toList();
    } catch (JacksonException e) {
      return List.of();
    }
  }
}
