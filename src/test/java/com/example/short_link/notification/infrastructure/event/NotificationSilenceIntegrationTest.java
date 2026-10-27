package com.example.short_link.notification.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.CollectionConnectedEvent;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationSilenceIntegrationTest {

  private static final Instant AT = Instant.parse("2026-10-27T03:00:00Z");

  @Autowired private RecordBlogNotificationUseCase recordUseCase;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private final List<Long> created = new ArrayList<>();

  private long member() {
    String id = UUID.randomUUID().toString();
    UserEntity user = new UserEntity(id + "@example.com", "google", id);
    user.claimUsername("s" + id.replace("-", "").substring(0, 12));
    long userId = users.save(user).getId();
    created.add(userId);
    return userId;
  }

  private void block(long blocker, long blocked) {
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        blocker,
        blocked);
  }

  private void mute(long user, long muted, boolean hideNotifications) {
    jdbc.update(
        "INSERT INTO user_mute (user_id, muted_user_id, hide_notifications, created_at)"
            + " VALUES (?, ?, ?, NOW(6))",
        user,
        muted,
        hideNotifications);
  }

  private List<Long> actorsNotifying(long recipient) {
    return jdbc.queryForList(
        "SELECT actor_user_id FROM notification WHERE recipient_user_id = ? ORDER BY id",
        Long.class,
        recipient);
  }

  @AfterEach
  void removeWhatTheTestCommitted() {
    for (Long id : created) {
      jdbc.update("DELETE FROM notification WHERE recipient_user_id = ?", id);
      jdbc.update("DELETE FROM user_block WHERE blocker_id = ? OR blocked_id = ?", id, id);
      jdbc.update("DELETE FROM user_mute WHERE user_id = ? OR muted_user_id = ?", id, id);
    }
    for (Long id : created) {
      jdbc.update("DELETE FROM users WHERE id = ?", id);
    }
  }

  @Test
  void aBlockOrAMuteWithNoticesKeepsTheNoticeAwayButAQuietMuteDoesNot() {
    long recipient = member();
    long blocked = member();
    long muted = member();
    long mutedQuietly = member();
    long stranger = member();
    block(recipient, blocked);
    mute(recipient, muted, true);
    mute(recipient, mutedQuietly, false);
    BlogInteractionNotificationListener listener =
        new BlogInteractionNotificationListener(recordUseCase);

    for (long actor : List.of(blocked, muted, mutedQuietly, stranger)) {
      listener.onBlogInteraction(BlogInteractionEvent.follow(recipient, actor, AT));
    }

    assertThat(actorsNotifying(recipient)).containsExactly(mutedQuietly, stranger);
  }

  @Test
  void aPathGrowsOnlyForContributorsWhoHaveNotSilencedTheCurator() {
    long curator = member();
    long blocking = member();
    long muting = member();
    long listening = member();
    block(blocking, curator);
    mute(muting, curator, true);

    new CollectionNotificationListener(recordUseCase)
        .onCollectionConnected(
            new CollectionConnectedEvent(
                curator, 42L, "c", null, null, List.of(blocking, muting, listening), AT));

    assertThat(actorsNotifying(blocking)).isEmpty();
    assertThat(actorsNotifying(muting)).isEmpty();
    assertThat(actorsNotifying(listening)).containsExactly(curator);
  }
}
