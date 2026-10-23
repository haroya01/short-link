package com.example.short_link.home.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.application.trends.LocalSeen;
import com.example.short_link.federation.domain.SeenItem;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.post.application.read.PostCard;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
class HomeHideSeenIntegrationTest {

  private static final String DEVICE = "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7";

  @Autowired private HomeFeedQueryService home;
  @Autowired private LocalSeen seen;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;

  private final Instant ahead = Instant.now().plus(Duration.ofDays(3));
  private long reader;
  private long writer;
  private SeenViewer viewer;

  @BeforeEach
  void aReaderAndAWriter() {
    reader = member("rd");
    writer = member("wr");
    em.flush();
    viewer = SeenViewer.member(reader);
  }

  @Test
  void aSeenNoteRoomNoteAndPostLeaveTheHiddenLatestButTheReadersOwnStay() {
    long room = room(writer, reader);
    long seenNote = note(writer, "PUBLIC", null);
    long freshNote = note(writer, "PUBLIC", null);
    long ownNote = note(reader, "PUBLIC", null);
    long roomNote = note(writer, "ROOM", room);
    long seenPost = post(writer, 50);
    long freshPost = post(writer, 40);
    long ownPost = post(reader, 30);
    Instant before = Instant.now().minusSeconds(60);
    stored(viewer, SeenItem.NOTE, seenNote, before);
    stored(viewer, SeenItem.NOTE, ownNote, before);
    stored(viewer, SeenItem.NOTE, roomNote, before);
    stored(viewer, SeenItem.POST, seenPost, before);
    stored(viewer, SeenItem.POST, ownPost, before);

    HomeFeedQueryService.Page hidden = latest(viewer, null, true);
    HomeFeedQueryService.Page whole = latest(viewer, null, false);

    assertThat(keys(hidden))
        .containsExactly(
            "post:" + freshPost, "post:" + ownPost, "note:" + ownNote, "note:" + freshNote);
    assertThat(keys(whole))
        .containsExactly(
            "post:" + seenPost,
            "post:" + freshPost,
            "post:" + ownPost,
            "note:" + roomNote,
            "note:" + ownNote,
            "note:" + freshNote,
            "note:" + seenNote);
  }

  @Test
  void whatIsStillHeldBeforeAFlushIsLeftOutToo() throws InterruptedException {
    long held = note(writer, "PUBLIC", null);
    long heldPost = post(writer, 10);
    long fresh = note(writer, "PUBLIC", null);
    seen.record(viewer, List.of("kurl:note:" + held, "kurl:post:" + heldPost));
    Thread.sleep(2);

    assertThat(keys(latest(viewer, null, true))).containsExactly("note:" + fresh);

    seen.flush();
    assertThat(keys(latest(viewer, null, true))).containsExactly("note:" + fresh);
  }

  @Test
  void sightingsDuringTheScrollDoNotMoveTheNextPage() {
    List<Long> written = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      written.add(note(writer, "PUBLIC", null));
    }
    stored(viewer, SeenItem.NOTE, written.get(4), Instant.now().minusSeconds(60));

    List<String> shown = new ArrayList<>();
    HomeFeedQueryService.Page at = page(viewer, null, 2);
    shown.addAll(keys(at));
    assertThat(at.next()).startsWith("h");
    Instant during = Instant.now().plusSeconds(1);
    for (int i = 0; i < 4; i++) {
      stored(viewer, SeenItem.NOTE, written.get(i), during);
    }
    for (int turn = 0; turn < 5 && at.next() != null && shown.size() < 8; turn++) {
      at = page(viewer, at.next(), 2);
      shown.addAll(keys(at));
    }

    assertThat(shown.stream().filter(key -> key.startsWith("note:")).limit(4))
        .containsExactly(
            "note:" + written.get(3),
            "note:" + written.get(2),
            "note:" + written.get(1),
            "note:" + written.get(0));
  }

  @Test
  void oneViewersSightingsLeaveAnotherAlone() {
    long note = note(writer, "PUBLIC", null);
    SeenViewer device = SeenViewer.of(null, DEVICE);
    stored(device, SeenItem.NOTE, note, Instant.now().minusSeconds(60));

    assertThat(keys(latest(viewer, null, true))).containsExactly("note:" + note);
    assertThat(
            keys(home.page(null, device, "latest", null, null, 40, true)).stream()
                .filter(key -> key.equals("note:" + note)))
        .isEmpty();
  }

  @Test
  void sinceFindsTheSeenMarkerAndBringsOnlyWhatAboveItWasNotSeen() {
    long marker = note(writer, "PUBLIC", null);
    HomeFeedQueryService.Page first = latest(viewer, null, true);
    stored(viewer, SeenItem.NOTE, marker, Instant.now().minusSeconds(1));
    long seenAbove = note(writer, "PUBLIC", null);
    long ownAbove = note(reader, "PUBLIC", null);
    long fresh = note(writer, "PUBLIC", null);
    stored(viewer, SeenItem.NOTE, seenAbove, Instant.now().minusSeconds(1));
    stored(viewer, SeenItem.NOTE, ownAbove, Instant.now().minusSeconds(1));

    HomeFeedQueryService.Page refreshed =
        home.page(reader, viewer, "latest", null, first.newest(), 20, true);

    assertThat(refreshed.gap()).isFalse();
    assertThat(keys(refreshed)).containsExactly("note:" + fresh, "note:" + ownAbove);
  }

  @Test
  void theStoreKeepsTheFirstSightingAndAnswersOnlyWhatWasSeenBeforeAsOf() {
    Instant first = Instant.parse("2026-10-23T01:00:00.123456Z");
    stored(viewer, SeenItem.NOTE, 1L, first);
    stored(viewer, SeenItem.POST, 1L, first.plusSeconds(60));
    seen.record(viewer, List.of("kurl:note:1"));
    seen.flush();

    assertThat(
            jdbc.queryForObject(
                "SELECT seen_at FROM local_seen WHERE viewer_key = ? AND item_kind = 1",
                LocalDateTime.class,
                viewer.keyBytes()))
        .isEqualTo(LocalDateTime.ofInstant(first, ZoneOffset.UTC));
    assertThat(
            seen.seenBefore(
                viewer,
                List.of(new SeenItem(SeenItem.NOTE, 1L), new SeenItem(SeenItem.POST, 1L)),
                first.plusSeconds(1)))
        .isEqualTo(Set.of(new SeenItem(SeenItem.NOTE, 1L)));
  }

  @Test
  void aMembersSightingsLeaveTheSignedOutDeviceAlone() {
    long note = note(writer, "PUBLIC", null);
    SeenViewer device = SeenViewer.of(null, DEVICE);
    stored(viewer, SeenItem.NOTE, note, Instant.now().minusSeconds(60));

    assertThat(keys(latest(viewer, null, true))).doesNotContain("note:" + note);
    assertThat(keys(signedOut(device))).contains("note:" + note);
  }

  @Test
  void aCursorFromBeforeHidingGoesOnWithoutHidingEvenWhenAskedToHide() {
    List<Long> written = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      written.add(note(writer, "PUBLIC", null));
    }
    stored(viewer, SeenItem.NOTE, written.get(0), Instant.now().minusSeconds(60));
    HomeFeedQueryService.Page first = home.page(reader, viewer, "latest", null, null, 1, false);
    assertThat(first.next()).doesNotStartWith("h");

    List<String> rest = new ArrayList<>();
    HomeFeedQueryService.Page at = first;
    for (int turn = 0; turn < 40 && at.next() != null; turn++) {
      at = home.page(reader, viewer, "latest", at.next(), null, 1, true);
      assertThat(at.next() == null || !at.next().startsWith("h")).isTrue();
      rest.addAll(keys(at));
    }

    assertThat(rest).contains("note:" + written.get(0));
  }

  @Test
  void aHidingCursorKeepsHidingWhenTheNextRequestLeavesTheParameterOut() {
    List<Long> written = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      written.add(note(writer, "PUBLIC", null));
    }
    stored(viewer, SeenItem.NOTE, written.get(0), Instant.now().minusSeconds(60));
    HomeFeedQueryService.Page at = home.page(reader, viewer, "latest", null, null, 1, true);
    List<String> shown = new ArrayList<>(keys(at));
    for (int turn = 0; turn < 40 && at.next() != null; turn++) {
      assertThat(at.next()).startsWith("h");
      at = home.page(reader, viewer, "latest", at.next(), null, 1, false);
      shown.addAll(keys(at));
    }

    assertThat(shown).doesNotContain("note:" + written.get(0));
    assertThat(shown)
        .contains("note:" + written.get(1), "note:" + written.get(2), "note:" + written.get(3));
  }

  @Test
  void aHiddenScrollInAnyPageSizeIsTheWholeListWithoutTheSeenButNotTheReadersOwn() {
    for (long seed = 1; seed <= 4; seed++) {
      Random random = new Random(seed);
      reader = member("rd");
      writer = member("wr");
      em.flush();
      viewer = SeenViewer.member(reader);
      List<String> hidden = new ArrayList<>();
      Instant before = Instant.now().minusSeconds(60);
      for (int i = 0; i < 14; i++) {
        boolean mine = random.nextInt(5) == 0;
        long note = note(mine ? reader : writer, "PUBLIC", null);
        if (random.nextBoolean()) {
          stored(viewer, SeenItem.NOTE, note, before);
          if (!mine) {
            hidden.add("note:" + note);
          }
        }
      }
      for (int i = 0; i < 6; i++) {
        boolean mine = random.nextInt(5) == 0;
        long post = post(mine ? reader : writer, seed * 10 + i);
        if (random.nextBoolean()) {
          stored(viewer, SeenItem.POST, post, before);
          if (!mine) {
            hidden.add("post:" + post);
          }
        }
      }
      List<String> whole = scroll(false, 40);
      int size = 1 + random.nextInt(5);

      List<String> scrolled = scroll(true, size);

      assertThat(scrolled)
          .as("seed %d, pages of %d", seed, size)
          .isEqualTo(whole.stream().filter(key -> !hidden.contains(key)).toList());
    }
  }

  @Test
  void postsPublishedAtTheSameInstantComeOnceEachAcrossPages() {
    List<String> posts = new ArrayList<>();
    for (int minute = 0; minute < 3; minute++) {
      for (int i = 0; i < 10; i++) {
        long author = i == 0 ? writer : member("tw");
        posts.add("post:" + post(author, 10 + minute));
      }
    }

    for (boolean hide : new boolean[] {false, true}) {
      for (int size = 1; size <= 7; size++) {
        List<String> shown = new ArrayList<>();
        HomeFeedQueryService.Page at = home.page(reader, viewer, "latest", null, null, size, hide);
        shown.addAll(keys(at));
        for (int turn = 0; turn < 500 && at.next() != null; turn++) {
          at = home.page(reader, viewer, "latest", at.next(), null, size, hide);
          shown.addAll(keys(at));
        }
        assertThat(shown.stream().filter(posts::contains).sorted().toList())
            .as("hide %s, pages of %d", hide, size)
            .isEqualTo(posts.stream().sorted().toList());
      }
    }
  }

  @Test
  void followedPostsPublishedAtTheSameInstantComeOnceEachAcrossPages() {
    List<String> posts = new ArrayList<>();
    for (int minute = 0; minute < 3; minute++) {
      for (int i = 0; i < 25; i++) {
        long author = member("fw");
        jdbc.update(
            "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
            reader,
            author);
        posts.add("post:" + post(author, 10 + minute));
      }
    }

    for (int size = 1; size <= 7; size++) {
      List<String> shown = new ArrayList<>();
      HomeFeedQueryService.Page at =
          home.page(reader, viewer, "following", null, null, size, false);
      shown.addAll(keys(at));
      for (int turn = 0; turn < 500 && at.next() != null; turn++) {
        at = home.page(reader, viewer, "following", at.next(), null, size, false);
        shown.addAll(keys(at));
      }
      assertThat(shown.stream().filter(posts::contains).sorted().toList())
          .as("pages of %d", size)
          .isEqualTo(posts.stream().sorted().toList());
    }
  }

  private List<String> scroll(boolean hide, int size) {
    List<String> shown = new ArrayList<>();
    HomeFeedQueryService.Page at = page(viewer, null, size, hide);
    shown.addAll(keys(at));
    for (int turn = 0; turn < 500 && at.next() != null; turn++) {
      at = page(viewer, at.next(), size, hide);
      shown.addAll(keys(at));
    }
    return shown;
  }

  private HomeFeedQueryService.Page signedOut(SeenViewer device) {
    HomeFeedQueryService.Page page = home.page(null, device, "latest", null, null, 40, true);
    return new HomeFeedQueryService.Page(
        page.items().stream().filter(this::ours).toList(), page.next(), page.newest(), page.gap());
  }

  private HomeFeedQueryService.Page latest(SeenViewer who, String cursor, boolean hide) {
    HomeFeedQueryService.Page page = home.page(reader, who, "latest", cursor, null, 40, hide);
    return new HomeFeedQueryService.Page(
        page.items().stream().filter(this::ours).toList(), page.next(), page.newest(), page.gap());
  }

  private HomeFeedQueryService.Page page(SeenViewer who, String cursor, int size) {
    return page(who, cursor, size, true);
  }

  private HomeFeedQueryService.Page page(SeenViewer who, String cursor, int size, boolean hide) {
    HomeFeedQueryService.Page page = home.page(reader, who, "latest", cursor, null, size, hide);
    return new HomeFeedQueryService.Page(
        page.items().stream().filter(this::ours).toList(), page.next(), page.newest(), page.gap());
  }

  private boolean ours(HomeFeedQueryService.Item item) {
    long author =
        item.kind().equals("note")
            ? ((NoteView) item.note()).author().id()
            : ((PostCard) item.post()).item().author().id();
    return author == reader || author == writer;
  }

  private static List<String> keys(HomeFeedQueryService.Page page) {
    return page.items().stream()
        .map(
            item ->
                item.kind().equals("note")
                    ? "note:" + ((NoteView) item.note()).id()
                    : "post:" + ((PostCard) item.post()).item().id())
        .toList();
  }

  private void stored(SeenViewer who, int kind, long id, Instant at) {
    jdbc.update(
        "INSERT INTO local_seen (viewer_kind, viewer_key, item_kind, item_id, seen_at)"
            + " VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE seen_at = seen_at",
        who.kind(),
        who.keyBytes(),
        kind,
        id,
        LocalDateTime.ofInstant(at, ZoneOffset.UTC));
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
            + " VALUES (?, 'hide seen', FALSE, NOW(6), NOW(6))",
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

  private long note(long author, String visibility, Long room) {
    jdbc.update(
        "INSERT INTO note (user_id, body, visibility, room_id, created_at)"
            + " VALUES (?, 'here', ?, ?, NOW(6))",
        author,
        visibility,
        room);
    return jdbc.queryForObject("SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, author);
  }

  private long post(long author, long minutesAhead) {
    String slug = "hide-" + UUID.randomUUID().toString().substring(0, 8);
    Timestamp at = Timestamp.from(ahead.plus(Duration.ofMinutes(minutesAhead)));
    jdbc.update(
        "INSERT INTO posts (user_id, slug, title, created_at, updated_at, status, audience,"
            + " published_at, body_text_length, language_tag)"
            + " VALUES (?, ?, ?, ?, ?, 'PUBLISHED', 'PUBLIC', ?, 500, 'ko')",
        author,
        slug,
        slug,
        at,
        at,
        at);
    return jdbc.queryForObject("SELECT id FROM posts WHERE slug = ?", Long.class, slug);
  }
}
