package com.example.short_link.home.application.read;

import static com.example.short_link.home.application.read.HomeAskFixtures.ask;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
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
class HomeFollowingSeriesNotesIntegrationTest {

  @Autowired private HomeFeedQueryService home;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;

  private final Instant ahead = Instant.now().plus(3, ChronoUnit.DAYS);
  private long reader;
  private long serial;
  private long followed;
  private long series;
  private long followedSeries;

  @BeforeEach
  void aReaderWhoSubscribesToASeriesWithoutFollowingItsWriter() {
    reader = member("rd");
    serial = member("se");
    followed = member("fo");
    em.flush();
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        reader,
        followed);
    series = series(serial);
    followedSeries = series(followed);
    subscribe(reader, series);
    subscribe(reader, followedSeries);
  }

  @Test
  void aSubscribedSeriesNoteReachesTheFollowingTabWithoutFollowingItsWriter() {
    long episode = note(serial, "PUBLIC", 60);
    inSeries(series, episode, 0);
    long outside = note(serial, "PUBLIC", 30);

    List<Long> shown = noteIds(home.page(ask(reader, null, "following", null, null, 20, false)));

    assertThat(shown).contains(episode).doesNotContain(outside);
  }

  @Test
  void aSeriesNoteBySomeoneTheReaderAlsoFollowsShowsOnce() {
    long both = note(followed, "PUBLIC", 60);
    inSeries(followedSeries, both, 0);

    List<Long> shown = noteIds(home.page(ask(reader, null, "following", null, null, 20, false)));

    assertThat(shown).containsOnlyOnce(both);
  }

  @Test
  void onlyWhatTheSeriesItselfShowsComesInThroughIt() {
    long unlisted = note(serial, "UNLISTED", 50);
    long restricted = note(serial, "PRIVATE", 40);
    inSeries(series, unlisted, 0);
    inSeries(series, restricted, 1);
    jdbc.update("INSERT INTO note_recipient (note_id, user_id) VALUES (?, ?)", restricted, reader);

    List<Long> shown = noteIds(home.page(ask(reader, null, "following", null, null, 20, false)));

    assertThat(shown).contains(unlisted).doesNotContain(restricted);
  }

  @Test
  void aSeriesNoteFromSomeoneTheReaderBlockedOrMutedStaysOut() {
    long episode = note(serial, "PUBLIC", 60);
    inSeries(series, episode, 0);
    jdbc.update(
        "INSERT INTO user_mute (user_id, muted_user_id, hide_notifications, created_at)"
            + " VALUES (?, ?, FALSE, NOW(6))",
        reader,
        serial);

    assertThat(noteIds(home.page(ask(reader, null, "following", null, null, 20, false))))
        .doesNotContain(episode);

    jdbc.update("DELETE FROM user_mute WHERE user_id = ?", reader);
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        serial,
        reader);

    assertThat(noteIds(home.page(ask(reader, null, "following", null, null, 20, false))))
        .doesNotContain(episode);
  }

  @Test
  void seriesNotesTakeTheirPlaceInTimeAcrossPagesAndRefreshes() {
    long older = note(followed, "PUBLIC", 180);
    long episode = note(serial, "PUBLIC", 120);
    inSeries(series, episode, 0);
    long newest = note(followed, "PUBLIC", 60);

    HomeFeedQueryService.Page first =
        home.page(ask(reader, null, "following", null, null, 2, false));
    assertThat(noteIds(first)).containsExactly(newest, episode);
    HomeFeedQueryService.Page second =
        home.page(ask(reader, null, "following", first.next(), null, 2, false));
    assertThat(noteIds(second)).containsExactly(older);

    long arrived = note(serial, "PUBLIC", 0);
    inSeries(series, arrived, 1);
    HomeFeedQueryService.Page refreshed =
        home.page(ask(reader, null, "following", null, first.newest(), 20, false));
    assertThat(refreshed.gap()).isFalse();
    assertThat(noteIds(refreshed)).containsExactly(arrived);
  }

  private static List<Long> noteIds(HomeFeedQueryService.Page page) {
    return page.items().stream()
        .filter(item -> item.kind().equals("note"))
        .map(item -> item.note().id())
        .toList();
  }

  private long member(String prefix) {
    String handle = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity(handle + "@example.com", "google", "g-" + handle);
    user.claimUsername(handle);
    return users.save(user).getId();
  }

  private long series(long owner) {
    String slug = "s-" + UUID.randomUUID().toString().substring(0, 8);
    jdbc.update(
        "INSERT INTO series (user_id, slug, title, created_at, updated_at)"
            + " VALUES (?, ?, ?, NOW(6), NOW(6))",
        owner,
        slug,
        slug);
    return jdbc.queryForObject(
        "SELECT id FROM series WHERE user_id = ? AND slug = ?", Long.class, owner, slug);
  }

  private void subscribe(long user, long seriesId) {
    jdbc.update(
        "INSERT INTO series_subscription (user_id, series_id, created_at) VALUES (?, ?, NOW(6))",
        user,
        seriesId);
  }

  private void inSeries(long seriesId, long noteId, int position) {
    jdbc.update(
        "INSERT INTO series_item (series_id, item_type, ref_id, position, created_at)"
            + " VALUES (?, 'NOTE', ?, ?, NOW(6))",
        seriesId,
        noteId,
        position);
  }

  private long note(long author, String visibility, long secondsBefore) {
    jdbc.update(
        "INSERT INTO note (user_id, body, visibility, created_at) VALUES (?, ?, ?, ?)",
        author,
        "series note " + secondsBefore,
        visibility,
        Timestamp.from(ahead.minusSeconds(secondsBefore)));
    return jdbc.queryForObject("SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, author);
  }
}
