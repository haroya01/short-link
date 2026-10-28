package com.example.short_link.home.application.read;

import static com.example.short_link.home.application.read.HomeAskFixtures.ask;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.seen.SeenViewer;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
class HomeBlogIntegrationTest {

  @Autowired private HomeFeedQueryService home;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;

  private final Instant ahead =
      Instant.now().plus(Duration.ofDays(400)).truncatedTo(ChronoUnit.MICROS);
  private long reader;
  private long writer;
  private long blocked;

  @BeforeEach
  void aReaderAWriterAndOneTheReaderBlocked() {
    reader = member("br");
    writer = member("bw");
    blocked = member("bb");
    em.flush();
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        reader,
        blocked);
  }

  @Test
  void theBlogIsEveryListedPostNewestPublishedFirstAndTheLaterIdFirstAtTheSameTime() {
    long newest = post(writer, "PUBLISHED", "PUBLIC", null, ahead);
    long earlierAtTie = post(writer, "PUBLISHED", "PUBLIC", null, ahead.minusSeconds(3_600));
    long laterAtTie = post(writer, "PUBLISHED", "PUBLIC", null, ahead.minusSeconds(3_600));
    long old = post(writer, "PUBLISHED", "PUBLIC", null, ahead.minus(Duration.ofDays(90)));
    post(writer, "PUBLISHED", "UNLISTED", null, ahead.minusSeconds(60));
    post(writer, "PUBLISHED", "FOLLOWERS", null, ahead.minusSeconds(60));
    post(writer, "PUBLISHED", "ROOM", room(writer, reader), ahead.minusSeconds(60));
    post(writer, "SCHEDULED", "PUBLIC", null, ahead.minusSeconds(60));
    post(writer, "DRAFT", "PUBLIC", null, ahead.minusSeconds(60));
    post(blocked, "PUBLISHED", "PUBLIC", null, ahead.minusSeconds(60));
    jdbc.update(
        "INSERT INTO local_seen (viewer_kind, viewer_key, item_kind, item_id, seen_at)"
            + " VALUES (?, ?, 2, ?, ?)",
        SeenViewer.MEMBER,
        SeenViewer.member(reader).keyBytes(),
        newest,
        utc(Instant.now().minusSeconds(60)));

    HomeFeedQueryService.Page first = blog(null, null, 2);
    List<Long> shown = new ArrayList<>(ids(first));
    String cursor = first.next();
    for (int turn = 0; turn < 50 && cursor != null && shown.size() < 4; turn++) {
      HomeFeedQueryService.Page page = blog(cursor, null, 2);
      shown.addAll(ids(page));
      cursor = page.next();
    }

    assertThat(first.next()).isEqualTo(marker(ahead.minusSeconds(3_600), laterAtTie));
    assertThat(first.newest()).isEqualTo(marker(ahead, newest));
    assertThat(shown).containsExactly(newest, laterAtTie, earlierAtTie, old);
    assertThat(first.items()).allMatch(item -> item.kind().equals("post"));
  }

  @Test
  void sinceBringsWhatCameAboveTheMarkerTheLaterIdAtTheSameTimeIncluded() {
    Instant at = ahead.minusSeconds(600);
    long below = post(writer, "PUBLISHED", "PUBLIC", null, at);
    long marker = post(writer, "PUBLISHED", "PUBLIC", null, at);
    long besideAbove = post(writer, "PUBLISHED", "PUBLIC", null, at);
    long above = post(writer, "PUBLISHED", "PUBLIC", null, ahead);
    post(writer, "PUBLISHED", "UNLISTED", null, ahead);

    HomeFeedQueryService.Page refreshed = blog(null, marker(at, marker), 20);

    assertThat(ids(refreshed)).containsExactly(above, besideAbove);
    assertThat(ids(refreshed)).doesNotContain(marker, below);
    assertThat(refreshed.gap()).isFalse();
    assertThat(refreshed.next()).isNull();
    assertThat(refreshed.newest()).isEqualTo(marker(ahead, above));

    HomeFeedQueryService.Page nothing = blog(null, marker(ahead, above), 20);
    assertThat(nothing.items()).isEmpty();
    assertThat(nothing.gap()).isFalse();
    assertThat(nothing.newest()).isEqualTo(marker(ahead, above));
  }

  @Test
  void moreThanFiftyAboveTheMarkerIsAGapAndAFreshFirstPage() {
    long marker = post(writer, "PUBLISHED", "PUBLIC", null, ahead);
    long top = 0;
    for (int i = 1; i <= 51; i++) {
      top = post(writer, "PUBLISHED", "PUBLIC", null, ahead.plusSeconds(i));
    }

    HomeFeedQueryService.Page refreshed = blog(null, marker(ahead, marker), 20);

    assertThat(refreshed.gap()).isTrue();
    assertThat(ids(refreshed)).hasSize(20).first().isEqualTo(top);
    assertThat(refreshed.next()).isNotNull();
    assertThat(blog(null, "nonsense", 20).gap()).isTrue();
  }

  private HomeFeedQueryService.Page blog(String cursor, String since, int size) {
    return home.page(ask(reader, SeenViewer.member(reader), "blog", cursor, since, size, true));
  }

  private List<Long> ids(HomeFeedQueryService.Page page) {
    Set<Long> ours = Set.of(writer, blocked, reader);
    return page.items().stream()
        .map(HomeFeedQueryService.Item::post)
        .filter(card -> ours.contains(card.item().author().id()))
        .map(card -> card.item().id())
        .toList();
  }

  private static String marker(Instant at, long id) {
    return "b" + ChronoUnit.MICROS.between(Instant.EPOCH, at) + "." + id;
  }

  private static LocalDateTime utc(Instant at) {
    return LocalDateTime.ofInstant(at, ZoneOffset.UTC);
  }

  private long member(String prefix) {
    String handle = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity(handle + "@example.com", "google", "g-" + handle);
    user.claimUsername(handle);
    return users.save(user).getId();
  }

  private long room(long owner, long member) {
    jdbc.update(
        "INSERT INTO room (owner_id, name, approval_required, created_at, updated_at)"
            + " VALUES (?, 'blog', FALSE, NOW(6), NOW(6))",
        owner);
    long room =
        jdbc.queryForObject("SELECT MAX(id) FROM room WHERE owner_id = ?", Long.class, owner);
    for (long user : new long[] {owner, member}) {
      jdbc.update(
          "INSERT INTO room_member (room_id, user_id, joined_at, hidden_from_feed)"
              + " VALUES (?, ?, NOW(6), FALSE)",
          room,
          user);
    }
    return room;
  }

  private long post(long author, String status, String audience, Long room, Instant at) {
    String slug = "bl-" + UUID.randomUUID().toString().substring(0, 8);
    jdbc.update(
        "INSERT INTO posts (user_id, slug, title, created_at, updated_at, status, audience,"
            + " room_id, published_at, scheduled_at, body_text_length, language_tag)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 500, 'ko')",
        author,
        slug,
        slug,
        utc(at),
        utc(at),
        status,
        audience,
        room,
        status.equals("DRAFT") ? null : utc(at),
        status.equals("SCHEDULED") ? utc(at) : null);
    return jdbc.queryForObject("SELECT id FROM posts WHERE slug = ?", Long.class, slug);
  }
}
