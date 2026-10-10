package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.federation.ServerBlockSql;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
class NotePeopleReaderAdapter implements NotePeopleReader {

  private static final String AUTHOR =
      "SELECT id, username, avatar_url, display_name FROM users"
          + " WHERE deleted_at IS NULL AND username IS NOT NULL AND ";

  @PersistenceContext private EntityManager em;

  @Override
  public Map<Long, NoteAuthor> activeAuthors(Collection<Long> userIds) {
    Map<Long, NoteAuthor> authors = new HashMap<>();
    if (userIds.isEmpty()) {
      return authors;
    }
    for (Object row :
        em.createNativeQuery(AUTHOR + "id IN (:ids)")
            .setParameter("ids", userIds)
            .getResultList()) {
      NoteAuthor author = author((Object[]) row);
      authors.put(author.id(), author);
    }
    return authors;
  }

  @Override
  public Map<Long, NoteAuthor> activeAuthors(
      Collection<Long> userIds, Collection<String> usernames) {
    if (usernames.isEmpty()) {
      return activeAuthors(userIds);
    }
    var query =
        userIds.isEmpty()
            ? em.createNativeQuery(AUTHOR + "username IN (:names)")
            : em.createNativeQuery(AUTHOR + "(id IN (:ids) OR username IN (:names))")
                .setParameter("ids", userIds);
    Map<Long, NoteAuthor> authors = new HashMap<>();
    for (Object row : query.setParameter("names", usernames).getResultList()) {
      NoteAuthor author = author((Object[]) row);
      authors.put(author.id(), author);
    }
    return authors;
  }

  @Override
  public Optional<NoteAuthor> activeByUsername(String username) {
    List<?> rows =
        em.createNativeQuery(AUTHOR + "username = :username")
            .setParameter("username", username)
            .getResultList();
    return rows.stream().findFirst().map(row -> author((Object[]) row));
  }

  // Keyed by the remote actor id. An account without a username shows as its server. An account on
  // a suspended server is left out, and a note without its author is shown nowhere.
  @Override
  public Map<Long, NoteAuthor> remoteAuthors(Collection<Long> remoteActorIds) {
    Map<Long, NoteAuthor> authors = new HashMap<>();
    if (remoteActorIds.isEmpty()) {
      return authors;
    }
    for (Object raw :
        em.createNativeQuery(
                "SELECT id, username, domain, avatar_url, display_name,"
                    + " COALESCE(profile_url, actor_uri) FROM federation_remote_actor ra"
                    + " WHERE ra.id IN (:ids) AND NOT "
                    + ServerBlockSql.suspended("ra.id"))
            .setParameter("ids", remoteActorIds)
            .getResultList()) {
      Object[] columns = (Object[]) raw;
      Long id = ((Number) columns[0]).longValue();
      String username = (String) columns[1];
      String domain = (String) columns[2];
      authors.put(
          id,
          NoteAuthor.remote(
              id,
              username == null ? domain : username + "@" + domain,
              (String) columns[3],
              (String) columns[4],
              (String) columns[5]));
    }
    return authors;
  }

  @Override
  public List<Long> followingIds(Long userId) {
    return em
        .createNativeQuery("SELECT following_id FROM user_follow WHERE follower_id = :userId")
        .setParameter("userId", userId)
        .getResultList()
        .stream()
        .map(id -> ((Number) id).longValue())
        .toList();
  }

  @Override
  public Set<Long> followersOf(Long userId, Collection<Long> candidates) {
    if (candidates.isEmpty()) {
      return Set.of();
    }
    Set<Long> followers = new HashSet<>();
    for (Object id :
        em.createNativeQuery(
                "SELECT follower_id FROM user_follow"
                    + " WHERE following_id = :userId AND follower_id IN (:candidates)")
            .setParameter("userId", userId)
            .setParameter("candidates", candidates)
            .getResultList()) {
      followers.add(((Number) id).longValue());
    }
    return followers;
  }

  @Override
  public boolean followsRemote(Long userId, Long remoteActorId) {
    return !em.createNativeQuery(
            "SELECT 1 FROM federation_following WHERE user_id = :userId"
                + " AND remote_actor_id = :actor AND accepted_at IS NOT NULL")
        .setParameter("userId", userId)
        .setParameter("actor", remoteActorId)
        .getResultList()
        .isEmpty();
  }

  private static NoteAuthor author(Object[] columns) {
    return new NoteAuthor(
        ((Number) columns[0]).longValue(),
        (String) columns[1],
        (String) columns[2],
        (String) columns[3]);
  }
}
