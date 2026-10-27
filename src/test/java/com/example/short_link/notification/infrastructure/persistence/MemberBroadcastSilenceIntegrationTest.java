package com.example.short_link.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberBroadcastSilenceIntegrationTest {

  @Autowired private NotificationFollowerReader readers;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private String tag;
  private Long author;
  private Long blockedByAuthor;
  private Long blockedAuthor;
  private Long noticeMuter;
  private Long quietMuter;
  private Long expiredMuter;
  private Long stranger;

  @BeforeEach
  void anAuthorAndTheirReaders() {
    tag = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    author = user("a");
    blockedByAuthor = user("x");
    blockedAuthor = user("y");
    noticeMuter = user("m");
    quietMuter = user("q");
    expiredMuter = user("e");
    stranger = user("s");
    block(author, blockedByAuthor);
    block(blockedAuthor, author);
    mute(noticeMuter, true, null);
    mute(quietMuter, false, null);
    mute(expiredMuter, true, Instant.now().minusSeconds(60));
  }

  @Test
  void theAuthorsEditReachesNoSharerTheyBlockedNorOneWhoBlockedOrNoticeMutedThem() {
    Long note = note(author);
    for (Long sharer : everyone()) {
      repost(sharer, note);
    }
    repost(author, note);

    assertThat(readers.noteSharersOf(note, author, null))
        .containsExactlyInAnyOrder(quietMuter, expiredMuter, stranger);
  }

  @Test
  void newWritingAndAnEditAreSilencedForTheSamePeople() {
    Long note = note(author);
    for (Long reader : everyone()) {
      follow(reader, author);
      repost(reader, note);
    }

    assertThat(readers.followerIdsOf(author))
        .containsExactlyInAnyOrderElementsOf(readers.noteSharersOf(note, author, null));
  }

  private List<Long> everyone() {
    return List.of(blockedByAuthor, blockedAuthor, noticeMuter, quietMuter, expiredMuter, stranger);
  }

  private Long user(String prefix) {
    String name = prefix + tag;
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private Long note(Long userId) {
    update("INSERT INTO note (user_id, body, created_at) VALUES (?1, 'shared', NOW(6))", userId);
    return ((Number)
            em.createNativeQuery("SELECT MAX(id) FROM note WHERE user_id = ?1")
                .setParameter(1, userId)
                .getSingleResult())
        .longValue();
  }

  private void block(Long blocker, Long blocked) {
    update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?1, ?2, NOW(6))",
        blocker,
        blocked);
  }

  private void mute(Long userId, boolean notifications, Instant expiresAt) {
    update(
        "INSERT INTO user_mute (user_id, muted_user_id, hide_notifications, expires_at,"
            + " created_at) VALUES (?1, ?2, ?3, ?4, NOW(6))",
        userId,
        author,
        notifications,
        expiresAt);
  }

  private void follow(Long follower, Long following) {
    update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?1, ?2, NOW(6))",
        follower,
        following);
  }

  private void repost(Long userId, Long noteId) {
    update(
        "INSERT INTO note_repost (note_id, user_id, created_at) VALUES (?1, ?2, NOW(6))",
        noteId,
        userId);
  }

  private void update(String sql, Object... values) {
    var query = em.createNativeQuery(sql);
    for (int i = 0; i < values.length; i++) {
      query.setParameter(i + 1, values[i]);
    }
    query.executeUpdate();
  }
}
