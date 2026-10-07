package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.repository.AccountExportReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Repository;

// One statement per export. Members who left (deleted, or never claimed a name) are left out, as
// is a mute that has run out.
@Repository
class AccountExportReaderAdapter implements AccountExportReader {

  private static final String ACTIVE = " u.deleted_at IS NULL AND u.username IS NOT NULL";

  @PersistenceContext private EntityManager em;

  @Override
  public List<Follow> follows(Long userId) {
    return rows(
        "SELECT u.username, NULL,"
            + " NOT EXISTS (SELECT 1 FROM note_repost_mute rm"
            + " WHERE rm.user_id = f.follower_id AND rm.muted_user_id = f.following_id),"
            + " f.notify_notes, 0 AS side, f.id"
            + " FROM user_follow f JOIN users u ON u.id = f.following_id"
            + " WHERE f.follower_id = :user AND"
            + ACTIVE
            + " UNION ALL SELECT a.username, a.domain, TRUE, FALSE, 1, ff.id"
            + " FROM federation_following ff JOIN federation_remote_actor a ON a.id = ff.remote_actor_id"
            + " WHERE ff.user_id = :user AND ff.accepted_at IS NOT NULL AND a.username IS NOT NULL"
            + " ORDER BY 5, 6",
        userId,
        row -> new Follow(account(row), truth(row[2]), truth(row[3])));
  }

  @Override
  public List<Account> blocks(Long userId) {
    return rows(
        "SELECT u.username, NULL FROM user_block b JOIN users u ON u.id = b.blocked_id"
            + " WHERE b.blocker_id = :user AND"
            + ACTIVE
            + " ORDER BY b.id",
        userId,
        AccountExportReaderAdapter::account);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Mute> mutes(Long userId) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT u.username, NULL, m.hide_notifications FROM user_mute m"
                    + " JOIN users u ON u.id = m.muted_user_id WHERE m.user_id = :user AND"
                    + ACTIVE
                    + " AND (m.expires_at IS NULL OR m.expires_at > :now) ORDER BY m.id")
            .setParameter("user", userId)
            .setParameter("now", Instant.now())
            .getResultList();
    return rows.stream().map(row -> new Mute(account(row), truth(row[2]))).toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<String> blockedDomains(Long userId) {
    List<Object> rows =
        em.createNativeQuery(
                "SELECT domain FROM user_domain_block WHERE user_id = :user ORDER BY id")
            .setParameter("user", userId)
            .getResultList();
    return rows.stream().map(Object::toString).toList();
  }

  @Override
  public List<Bookmark> bookmarks(Long userId) {
    return rows(
        "SELECT n.id, n.uri FROM note_bookmark b JOIN note n ON n.id = b.note_id"
            + " WHERE b.user_id = :user ORDER BY b.id",
        userId,
        row ->
            new Bookmark(((Number) row[0]).longValue(), row[1] == null ? null : row[1].toString()));
  }

  @Override
  public List<ListMember> lists(Long userId) {
    return rows(
        "SELECT l.title, u.username, NULL FROM note_list l"
            + " JOIN note_list_member m ON m.list_id = l.id JOIN users u ON u.id = m.member_id"
            + " WHERE l.user_id = :user AND"
            + ACTIVE
            + " ORDER BY l.id, m.created_at",
        userId,
        row -> new ListMember(row[0].toString(), new Account(row[1].toString(), null)));
  }

  @SuppressWarnings("unchecked")
  private <T> List<T> rows(String sql, Long userId, Function<Object[], T> map) {
    return ((List<Object>) em.createNativeQuery(sql).setParameter("user", userId).getResultList())
        .stream().map(raw -> map.apply((Object[]) raw)).toList();
  }

  private static Account account(Object[] row) {
    return new Account(row[0].toString(), row[1] == null ? null : row[1].toString());
  }

  private static boolean truth(Object value) {
    return switch (value) {
      case null -> false;
      case Boolean b -> b;
      case Number n -> n.intValue() != 0;
      default -> Boolean.parseBoolean(value.toString());
    };
  }
}
