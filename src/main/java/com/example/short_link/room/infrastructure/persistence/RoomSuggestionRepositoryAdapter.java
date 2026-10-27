package com.example.short_link.room.infrastructure.persistence;

import static com.example.short_link.room.infrastructure.persistence.RoomPeopleRows.PERSON;
import static com.example.short_link.room.infrastructure.persistence.RoomPeopleRows.people;

import com.example.short_link.common.user.HeardSql;
import com.example.short_link.room.domain.RoomPerson;
import com.example.short_link.room.domain.repository.RoomSuggestionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class RoomSuggestionRepositoryAdapter implements RoomSuggestionRepository {

  @PersistenceContext private EntityManager em;

  @Override
  public List<RoomPerson> friendsWithoutARoom(Long userId, int limit) {
    return people(
        em.createNativeQuery(
                "SELECT "
                    + PERSON
                    + " FROM user_follow a"
                    + " JOIN user_follow b ON b.follower_id = a.following_id"
                    + " AND b.following_id = a.follower_id"
                    + " JOIN users u ON u.id = a.following_id"
                    + " AND u.deleted_at IS NULL AND u.username IS NOT NULL"
                    + " WHERE a.follower_id = :viewer"
                    + " AND NOT EXISTS (SELECT 1 FROM room_suggestion_dismissal d"
                    + " WHERE d.user_id = :viewer AND d.friend_id = u.id)"
                    + " AND NOT EXISTS (SELECT 1 FROM room_member mine"
                    + " JOIN room_member theirs ON theirs.room_id = mine.room_id"
                    + " WHERE mine.user_id = :viewer AND theirs.user_id = u.id)"
                    + HeardSql.unblocked("u.id")
                    + " ORDER BY GREATEST(a.created_at, b.created_at) DESC, u.id DESC")
            .setParameter("viewer", userId)
            .setMaxResults(limit)
            .getResultList());
  }

  @Override
  public void dismissSuggestions(Long userId, Collection<Long> friendIds, Instant at) {
    for (Long friendId : friendIds) {
      em.createNativeQuery(
              "INSERT IGNORE INTO room_suggestion_dismissal (user_id, friend_id, created_at)"
                  + " VALUES (:user, :friend, :at)")
          .setParameter("user", userId)
          .setParameter("friend", friendId)
          .setParameter("at", at)
          .executeUpdate();
    }
  }
}
