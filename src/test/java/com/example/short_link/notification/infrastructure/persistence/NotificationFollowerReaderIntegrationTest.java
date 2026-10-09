package com.example.short_link.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationFollowerReaderIntegrationTest {

  @Autowired private NotificationFollowerReader reader;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbc;

  private long author;
  private long plain;
  private long blockedByAuthor;
  private long blockingAuthor;
  private long mutingNotices;
  private long mutingQuietly;
  private long muteEnded;

  private long user(String handle) {
    UserEntity u = new UserEntity(handle + "@x.com", "google", "g-" + handle);
    u.claimUsername(handle);
    return userRepository.save(u).getId();
  }

  private void follow(long follower, boolean notes) {
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at, notify_notes)"
            + " VALUES (?, ?, NOW(6), ?)",
        follower,
        author,
        notes);
  }

  private void block(long blocker, long blocked) {
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        blocker,
        blocked);
  }

  // Entities store instants as UTC wall-clock time, so the fixture writes the mute's end the same
  // way.
  private void mute(long user, boolean notices, boolean ended) {
    jdbc.update(
        "INSERT INTO user_mute (user_id, muted_user_id, hide_notifications, expires_at, created_at)"
            + " VALUES (?, ?, ?, "
            + (ended ? "UTC_TIMESTAMP(6) - INTERVAL 1 HOUR" : "NULL")
            + ", NOW(6))",
        user,
        author,
        notices);
  }

  @BeforeEach
  void setUp() {
    author = user("nfr-author");
    plain = user("nfr-plain");
    blockedByAuthor = user("nfr-blocked");
    blockingAuthor = user("nfr-blocking");
    mutingNotices = user("nfr-muting");
    mutingQuietly = user("nfr-quiet");
    muteEnded = user("nfr-ended");
    for (long follower :
        new long[] {
          plain, blockedByAuthor, blockingAuthor, mutingNotices, mutingQuietly, muteEnded
        }) {
      follow(follower, true);
    }
    block(author, blockedByAuthor);
    block(blockingAuthor, author);
    mute(mutingNotices, true, false);
    mute(mutingQuietly, false, false);
    mute(muteEnded, true, true);
  }

  @Test
  void aNewPostReachesNoFollowerBlockedEitherWayOrMutingTheAuthorsNotices() {
    assertThat(reader.followerIdsOf(author))
        .containsExactlyInAnyOrder(plain, mutingQuietly, muteEnded);
  }

  @Test
  void noteSubscribersAreSilencedTheSameWay() {
    assertThat(reader.noteSubscribersOf(author))
        .containsExactlyInAnyOrder(plain, mutingQuietly, muteEnded);
  }

  @Test
  void aFollowWithoutTheNoteBellHearsOfPostsButNotNotes() {
    long postsOnly = user("nfr-posts-only");
    follow(postsOnly, false);

    assertThat(reader.followerIdsOf(author)).contains(postsOnly);
    assertThat(reader.noteSubscribersOf(author)).doesNotContain(postsOnly);
  }
}
