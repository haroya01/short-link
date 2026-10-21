package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.common.federation.ServerBlockSql;
import com.example.short_link.common.user.HeardSql;
import com.example.short_link.user.domain.repository.FollowListReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
class FollowListReaderAdapter implements FollowListReader {

  private static final String LIVE = " u.deleted_at IS NULL AND u.username IS NOT NULL";

  private static final String DOMAIN_BLOCKED =
      "EXISTS (SELECT 1 FROM user_domain_block d WHERE d.user_id = :viewer AND d.domain = a.domain)";

  private static final String LOCAL_FOLLOWERS =
      "SELECT 0 AS kind, f.follower_id AS id, f.created_at AS followed_at, f.id AS row_id"
          + " FROM user_follow f JOIN users u ON u.id = f.follower_id"
          + " WHERE f.following_id = :user AND"
          + LIVE;

  private static final String LOCAL_FOLLOWING =
      "SELECT 0 AS kind, f.following_id AS id, f.created_at AS followed_at, f.id AS row_id"
          + " FROM user_follow f JOIN users u ON u.id = f.following_id"
          + " WHERE f.follower_id = :user AND"
          + LIVE;

  private static final String ACCOUNT =
      "SELECT a.id, a.username, a.domain, a.display_name, a.avatar_url,"
          + " COALESCE(a.profile_url, a.actor_uri),"
          + " vf.id IS NOT NULL AND vf.accepted_at IS NOT NULL,"
          + " vf.id IS NOT NULL AND vf.accepted_at IS NULL, "
          + DOMAIN_BLOCKED
          + ", "
          + HeardSql.remoteBlocked(":viewer", "a.id")
          + ", "
          + HeardSql.remoteMuted(":viewer", "a.id")
          + " FROM federation_remote_actor a LEFT JOIN federation_following vf"
          + " ON vf.user_id = :viewer AND vf.remote_actor_id = a.id"
          + " WHERE a.id IN (:ids)";

  private static final String COUNTS =
      "SELECT (SELECT COUNT(*) FROM user_follow f JOIN users u ON u.id = f.follower_id"
          + " WHERE f.following_id = :user AND"
          + LIVE
          + "), (SELECT COUNT(*) FROM user_follow f JOIN users u ON u.id = f.following_id"
          + " WHERE f.follower_id = :user AND"
          + LIVE
          + "), "
          + remoteCount("federation_follower")
          + ", "
          + remoteCount("federation_following");

  @PersistenceContext private EntityManager em;
  private final Clock clock;

  FollowListReaderAdapter(Clock clock) {
    this.clock = clock;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Entry> page(Long userId, Side side, Long viewerId, int offset, int limit) {
    String local = side == Side.FOLLOWERS ? LOCAL_FOLLOWERS : LOCAL_FOLLOWING;
    String remote = side == Side.FOLLOWERS ? "federation_follower" : "federation_following";
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT m.kind, m.id FROM ("
                    + local
                    + " UNION ALL SELECT 1, a.id, r.created_at, r.id FROM "
                    + remote
                    + " r JOIN federation_remote_actor a ON a.id = r.remote_actor_id"
                    + " WHERE r.user_id = :user AND r.accepted_at IS NOT NULL AND NOT "
                    + ServerBlockSql.suspends("a.domain")
                    + HeardSql.unblockedRemote("a.id")
                    + " AND NOT "
                    + DOMAIN_BLOCKED
                    + ") m ORDER BY m.followed_at DESC, m.kind, m.row_id DESC")
            .setParameter("user", userId)
            .setParameter("viewer", HeardSql.viewer(viewerId))
            .setFirstResult(offset)
            .setMaxResults(limit)
            .getResultList();
    return rows.stream()
        .map(row -> new Entry(((Number) row[0]).intValue() == 1, ((Number) row[1]).longValue()))
        .toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<Long, RemoteAccount> remoteAccounts(Collection<Long> remoteActorIds, Long viewerId) {
    Map<Long, RemoteAccount> accounts = new HashMap<>();
    if (remoteActorIds.isEmpty()) {
      return accounts;
    }
    List<Object[]> rows =
        em.createNativeQuery(ACCOUNT)
            .setParameter("ids", remoteActorIds)
            .setParameter("viewer", HeardSql.viewer(viewerId))
            .setParameter("now", clock.instant())
            .getResultList();
    for (Object[] row : rows) {
      Long id = ((Number) row[0]).longValue();
      String username = row[1] == null ? "" : (String) row[1];
      String domain = (String) row[2];
      accounts.put(
          id,
          new RemoteAccount(
              id,
              username + "@" + domain,
              username,
              domain,
              (String) row[3],
              (String) row[4],
              (String) row[5],
              flag(row[6]),
              flag(row[7]),
              flag(row[8]),
              flag(row[9]),
              flag(row[10])));
    }
    return accounts;
  }

  @Override
  public Counts counts(Long userId) {
    Object[] row =
        (Object[]) em.createNativeQuery(COUNTS).setParameter("user", userId).getSingleResult();
    return new Counts(count(row[0]), count(row[1]), count(row[2]), count(row[3]));
  }

  private static String remoteCount(String table) {
    return "(SELECT COUNT(*) FROM "
        + table
        + " r JOIN federation_remote_actor a ON a.id = r.remote_actor_id"
        + " WHERE r.user_id = :user AND r.accepted_at IS NOT NULL AND NOT "
        + ServerBlockSql.suspends("a.domain")
        + ")";
  }

  private static long count(Object value) {
    return ((Number) value).longValue();
  }

  private static boolean flag(Object value) {
    return value instanceof Number n ? n.intValue() != 0 : Boolean.TRUE.equals(value);
  }
}
