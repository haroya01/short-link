package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.common.federation.ServerBlockSql;
import com.example.short_link.common.user.HeardSql;
import com.example.short_link.federation.domain.RemoteAccountMatch;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class RemoteActorRepositoryAdapter implements RemoteActorRepository {

  private static final String ACCT = "CONCAT(a.username, '@', a.domain)";

  // A name finds only an account that lets itself be found (toot:discoverable), unless the viewer
  // follows or asked to follow it or typed its whole handle.
  private static final String SEARCH =
      "SELECT a.id, a.username, a.domain, a.display_name, a.avatar_url,"
          + " COALESCE(a.profile_url, a.actor_uri), f.accepted_at IS NOT NULL,"
          + " f.id IS NOT NULL AND f.accepted_at IS NULL, "
          + HeardSql.remoteMuted(":viewer", "a.id")
          + " FROM federation_remote_actor a"
          + " LEFT JOIN federation_following f"
          + " ON f.user_id = :viewer AND f.remote_actor_id = a.id"
          + " WHERE a.username IS NOT NULL AND (MATCHES)"
          + " AND (a.discoverable OR f.id IS NOT NULL OR HANDLE)"
          + " AND NOT "
          + ServerBlockSql.suspends("a.domain")
          + " AND NOT EXISTS (SELECT 1 FROM user_domain_block d"
          + " WHERE d.user_id = :viewer AND d.domain = a.domain)"
          + HeardSql.unblockedRemote("a.id")
          + " ORDER BY f.accepted_at IS NOT NULL DESC, EXACT = :exact DESC,"
          + " a.username, a.domain, a.id";

  private static final String NAMES = "a.username LIKE :prefix OR a.display_name LIKE :prefix";

  private static final String BY_NAME =
      SEARCH.replace("MATCHES", NAMES).replace("HANDLE", "FALSE").replace("EXACT", "a.username");

  private static final String BY_ACCT =
      SEARCH
          .replace("MATCHES", NAMES + " OR (a.username = :user AND a.domain LIKE :domainPrefix)")
          .replace("HANDLE", ACCT + " = :exact")
          .replace("EXACT", ACCT);

  private static final String SUGGESTIONS =
      "SELECT a.id, a.username, a.domain, a.display_name, a.avatar_url,"
          + " COALESCE(a.profile_url, a.actor_uri)"
          + " FROM user_follow uf"
          + " JOIN federation_following ff"
          + " ON ff.user_id = uf.following_id AND ff.accepted_at IS NOT NULL"
          + " JOIN federation_remote_actor a ON a.id = ff.remote_actor_id"
          + " WHERE uf.follower_id = :viewer AND a.username IS NOT NULL AND a.discoverable"
          + " AND NOT EXISTS (SELECT 1 FROM federation_following mf"
          + " WHERE mf.user_id = :viewer AND mf.remote_actor_id = a.id)"
          + " AND NOT "
          + ServerBlockSql.suspended("a.id")
          + " AND NOT "
          + ServerBlockSql.limitedFor("a.id", ":viewer")
          + " AND NOT EXISTS (SELECT 1 FROM user_domain_block d"
          + " WHERE d.user_id = :viewer AND d.domain = a.domain)"
          + HeardSql.heardRemote("a.id")
          + " GROUP BY a.id ORDER BY COUNT(*) DESC, a.id DESC";

  private final JpaRemoteActorRepository jpa;
  private final EntityManager em;

  @Override
  public Optional<RemoteActorEntity> findByActorUri(String actorUri) {
    return jpa.findByActorUri(actorUri);
  }

  @Override
  public List<RemoteActorEntity> findAllByActorUri(Collection<String> actorUris) {
    return actorUris.isEmpty() ? List.of() : jpa.findAllByActorUriIn(actorUris);
  }

  @Override
  public Optional<RemoteActorEntity> findByKeyId(String keyId) {
    return jpa.findFirstByKeyId(keyId);
  }

  @Override
  public Optional<RemoteActorEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public Optional<RemoteActorEntity> findByAcct(String username, String domain) {
    return jpa.findFirstByUsernameAndDomainOrUsernameAndAccountDomain(
        username, domain, username, domain);
  }

  @Override
  public List<RemoteActorEntity> findAllById(Collection<Long> ids) {
    return ids.isEmpty() ? List.of() : jpa.findAllById(ids);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<RemoteAccountMatch> search(Long viewerId, String term, int limit, Instant now) {
    int at = term.indexOf('@');
    var query =
        em.createNativeQuery(at >= 0 ? BY_ACCT : BY_NAME)
            .setParameter("viewer", HeardSql.viewer(viewerId))
            .setParameter("prefix", escape(term) + "%")
            .setParameter("exact", term)
            .setParameter("now", now);
    if (at >= 0) {
      query
          .setParameter("user", term.substring(0, at))
          .setParameter("domainPrefix", escape(term.substring(at + 1)) + "%");
    }
    List<Object[]> rows = query.setMaxResults(limit).getResultList();
    return rows.stream().map(RemoteActorRepositoryAdapter::match).toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<RemoteAccountMatch> suggestions(Long viewerId, int limit, Instant now) {
    List<Object[]> rows =
        em.createNativeQuery(SUGGESTIONS)
            .setParameter("viewer", viewerId)
            .setParameter("now", now)
            .setMaxResults(limit)
            .getResultList();
    return rows.stream()
        .map(
            row ->
                new RemoteAccountMatch(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    (String) row[4],
                    (String) row[5],
                    false,
                    false,
                    false))
        .toList();
  }

  @Override
  public RemoteActorEntity saveAndFlush(RemoteActorEntity actor) {
    return jpa.saveAndFlush(actor);
  }

  @Override
  public void delete(RemoteActorEntity actor) {
    jpa.delete(actor);
  }

  @Override
  @Transactional
  public boolean claimOutbox(Long id, Instant at, Instant lookedAtBefore) {
    return em.createNativeQuery(
                "UPDATE federation_remote_actor SET outbox_checked_at = :at WHERE id = :id"
                    + " AND (outbox_checked_at IS NULL OR outbox_checked_at < :before)")
            .setParameter("at", at)
            .setParameter("id", id)
            .setParameter("before", lookedAtBefore)
            .executeUpdate()
        == 1;
  }

  @Override
  @Transactional
  public int markListed(Collection<Long> ids, Instant at) {
    if (ids.isEmpty()) {
      return 0;
    }
    return em.createNativeQuery(
            "UPDATE federation_remote_actor SET directory_seen_at = :at, discoverable = TRUE"
                + " WHERE id IN (:ids)")
        .setParameter("at", at)
        .setParameter("ids", ids)
        .executeUpdate();
  }

  @Override
  @Transactional
  public int forgetUnlisted(Instant cutoff) {
    return em.createNativeQuery(
            "UPDATE federation_remote_actor SET discoverable = FALSE WHERE discoverable"
                + " AND directory_seen_at < :cutoff AND fetched_at < :cutoff")
        .setParameter("cutoff", cutoff)
        .executeUpdate();
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static RemoteAccountMatch match(Object[] row) {
    return new RemoteAccountMatch(
        ((Number) row[0]).longValue(),
        (String) row[1],
        (String) row[2],
        (String) row[3],
        (String) row[4],
        (String) row[5],
        flag(row[6]),
        flag(row[7]),
        flag(row[8]));
  }

  private static boolean flag(Object value) {
    return value instanceof Number n ? n.intValue() != 0 : Boolean.TRUE.equals(value);
  }
}
