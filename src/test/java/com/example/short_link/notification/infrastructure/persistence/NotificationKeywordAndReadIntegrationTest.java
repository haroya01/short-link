package com.example.short_link.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.domain.NoteFilterEntity;
import com.example.short_link.note.domain.NoteFilterEntity.Action;
import com.example.short_link.note.domain.NoteFilterEntity.Context;
import com.example.short_link.note.domain.repository.NoteFilterRepository;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.policy.KeywordFilter;
import com.example.short_link.notification.domain.repository.NotificationKeywordFilterReader;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
class NotificationKeywordAndReadIntegrationTest {

  @Autowired private NotificationKeywordFilterReader keywordFilters;
  @Autowired private NoteFilterRepository noteFilters;
  @Autowired private NotificationRepository notifications;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbc;

  private long reader;
  private long other;
  private long actor;

  private long user(String handle) {
    UserEntity u = new UserEntity(handle + "@x.com", "google", "g-" + handle);
    u.claimUsername(handle);
    return userRepository.save(u).getId();
  }

  private void filter(
      long userId, String phrase, Set<Context> contexts, Action action, Instant end) {
    noteFilters.save(new NoteFilterEntity(userId, phrase, false, contexts, action, end));
  }

  private long notice(String groupKey, boolean filtered) {
    return notifications
        .save(
            new NotificationEntity(
                reader, NotificationType.NOTE_REPLY, actor, null, "{}", groupKey, filtered))
        .getId();
  }

  private boolean unread(long id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT read_at IS NULL FROM notification WHERE id = ?", Boolean.class, id));
  }

  @BeforeEach
  void setUp() {
    reader = user("nkr-reader");
    other = user("nkr-other");
    actor = user("nkr-actor");
  }

  @Test
  void onlyLiveFiltersThatCoverNotificationsReachTheNoticeWriter() {
    Instant now = Instant.now();
    filter(reader, "스포일러", EnumSet.of(Context.NOTIFICATIONS), Action.HIDE, null);
    filter(reader, "결말", EnumSet.of(Context.HOME, Context.NOTIFICATIONS), Action.WARN, null);
    filter(reader, "홈만", EnumSet.of(Context.HOME), Action.HIDE, null);
    filter(
        reader,
        "끝난",
        EnumSet.of(Context.NOTIFICATIONS),
        Action.HIDE,
        now.minus(Duration.ofHours(1)));
    filter(
        other, "남의", EnumSet.of(Context.NOTIFICATIONS), Action.WARN, now.plus(Duration.ofDays(1)));

    Map<Long, List<KeywordFilter>> live = keywordFilters.activeFor(List.of(reader, other), now);

    assertThat(live.get(reader))
        .containsExactlyInAnyOrder(
            new KeywordFilter("스포일러", false, true), new KeywordFilter("결말", false, false));
    assertThat(live.get(other)).containsExactly(new KeywordFilter("남의", false, false));
    assertThat(keywordFilters.activeFor(List.of(actor), now)).isEmpty();
  }

  @Test
  void readingAllLeavesNoticesThePolicyKeptAsideUnread() {
    long shown = notice(null, false);
    long keptAside = notice(null, true);

    notifications.markAllRead(reader, Instant.now());

    assertThat(unread(shown)).isFalse();
    assertThat(unread(keptAside)).isTrue();
  }

  @Test
  void readingAGroupLeavesItsKeptAsideMembersUnread() {
    long older = notice("NOTE_REPLY:5:2026-10-09", false);
    long keptAside = notice("NOTE_REPLY:5:2026-10-09", true);
    long newest = notice("NOTE_REPLY:5:2026-10-09", false);

    notifications.markRead(newest, reader, Instant.now());

    assertThat(unread(older)).isFalse();
    assertThat(unread(newest)).isFalse();
    assertThat(unread(keptAside)).isTrue();
  }
}
