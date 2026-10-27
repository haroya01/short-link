package com.example.short_link.home.application.read;

import static com.example.short_link.home.application.read.HomeAskFixtures.ask;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.SeenItem;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.domain.repository.ForYouSignalReader;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.PooledNote;
import com.example.short_link.post.application.read.PooledPost;
import com.example.short_link.post.application.read.PostCard;
import com.example.short_link.post.application.read.PublicFeedQueryService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "short-link.federation.latest.pull=true")
@Transactional
class HomeForYouIntegrationTest {

  @Autowired private HomeFeedQueryService home;
  @Autowired private NoteQueryService notes;
  @Autowired private PublicFeedQueryService posts;
  @Autowired private ForYouSignalReader signals;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;

  private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
  private long reader;
  private long writer;
  private long stranger;
  private SeenViewer viewer;

  @BeforeEach
  void aReaderAWriterAndAStranger() {
    reader = member("fr");
    writer = member("fw");
    stranger = member("fs");
    em.flush();
    viewer = SeenViewer.member(reader);
  }

  @Test
  void theListHoldsTheWeeksPublicNotesAndPostsAndPinsOnlyTheReadersFreshNote() {
    long kept = note(writer, "PUBLIC", null, null, ago(120));
    long parent = note(writer, "PUBLIC", null, null, ago(300));
    long reply = note(writer, "PUBLIC", null, parent, ago(100));
    note(writer, "UNLISTED", null, null, ago(100));
    note(writer, "DIRECT", null, null, ago(100));
    note(writer, "ROOM", room(writer, reader), null, ago(100));
    note(writer, "PUBLIC", null, null, now.minus(Duration.ofDays(7)).minusSeconds(60));
    long blocked = note(stranger, "PUBLIC", null, null, ago(90));
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        reader,
        stranger);
    long post = post(writer, "PUBLISHED", "PUBLIC", ago(180));
    post(writer, "DRAFT", "PUBLIC", ago(170));
    post(writer, "PUBLISHED", "UNLISTED", ago(160));
    long fresh = note(reader, "PUBLIC", null, null, ago(10));
    long older = note(reader, "PUBLIC", null, null, ago(61));

    List<String> shown = keys(forYou(null, 40, true));

    assertThat(shown).first().isEqualTo("note:" + fresh);
    assertThat(shown)
        .containsExactlyInAnyOrder(
            "note:" + fresh, "note:" + kept, "note:" + parent, "post:" + post);
    assertThat(shown).doesNotContain("note:" + older, "note:" + blocked, "note:" + reply);
  }

  @Test
  void hidingLeavesOutWhatTheReaderSawTheirOwnFreshNoteIncludedButNotWithoutIt() {
    long seenNote = note(writer, "PUBLIC", null, null, ago(30));
    long seenPost = post(writer, "PUBLISHED", "PUBLIC", ago(40));
    long unseen = note(writer, "PUBLIC", null, null, ago(50));
    long fresh = note(reader, "PUBLIC", null, null, ago(5));
    Instant before = now.minusSeconds(60);
    stored(SeenItem.NOTE, seenNote, before);
    stored(SeenItem.POST, seenPost, before);
    stored(SeenItem.NOTE, fresh, before);

    assertThat(keys(forYou(null, 40, true))).containsExactly("note:" + unseen);
    List<String> whole = keys(forYou(null, 40, false));
    assertThat(whole).first().isEqualTo("note:" + fresh);
    assertThat(whole)
        .containsExactlyInAnyOrder(
            "note:" + fresh, "note:" + seenNote, "post:" + seenPost, "note:" + unseen);
  }

  @Test
  void aScrollGoesOnFromTheFirstPagesTimeAndKeepsHidingWhenTheNextAskLeavesItOut() {
    List<Long> written = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      written.add(note(writer, "PUBLIC", null, null, ago(20 + i)));
    }
    stored(SeenItem.NOTE, written.get(1), now.minusSeconds(60));

    HomeFeedQueryService.Page first =
        home.page(ask(reader, viewer, "for-you", null, null, 1, true));
    List<String> shown = new ArrayList<>(keys(first));
    String cursor = first.next();
    long arrived = note(writer, "PUBLIC", null, null, Instant.now().plusSeconds(1));
    stored(SeenItem.NOTE, written.get(2), Instant.now().plusSeconds(1));
    for (int turn = 0; turn < 400 && cursor != null; turn++) {
      assertThat(cursor).startsWith("rh");
      HomeFeedQueryService.Page page =
          home.page(ask(reader, viewer, "for-you", cursor, null, 1, false));
      shown.addAll(keys(page));
      cursor = page.next();
    }

    assertThat(shown.stream().filter(key -> !key.equals("note:" + arrived)))
        .containsExactly(
            "note:" + written.get(0), "note:" + written.get(2), "note:" + written.get(3));
    assertThat(shown).doesNotContain("note:" + arrived);
  }

  @Test
  void aPostIsRankedByItsLikesQuotingNotesAndHighlightsAndANoteByItsOwnCounts() {
    long post = post(writer, "PUBLISHED", "PUBLIC", ago(30));
    jdbc.update("UPDATE posts SET like_count = 5 WHERE id = ?", post);
    jdbc.update("INSERT INTO post_tag (post_id, ordinal, tag) VALUES (?, 0, 'Java')", post);
    for (String visibility : List.of("PUBLIC", "PUBLIC", "DIRECT")) {
      long quote = note(stranger, visibility, null, null, ago(20));
      jdbc.update("UPDATE note SET quoted_post_id = ? WHERE id = ?", post, quote);
    }
    jdbc.update(
        "INSERT INTO post_highlight (post_id, user_id, block_order, start_offset, end_offset,"
            + " quote, created_at) VALUES (?, ?, 0, 0, 4, 'here', NOW(6))",
        post,
        stranger);
    long note = note(writer, "PUBLIC", null, null, ago(25));
    jdbc.update(
        "INSERT INTO note_like (note_id, user_id, created_at) VALUES (?, ?, NOW(6))",
        note,
        stranger);
    jdbc.update("INSERT INTO note_tag (note_id, tag) VALUES (?, 'java')", note);

    PooledPost pooled =
        posts.pool(reader, now.minus(Duration.ofDays(7)), now, 500).stream()
            .filter(row -> row.id() == post)
            .findFirst()
            .orElseThrow();
    PooledNote pooledNote =
        notes.pool(reader, now.minus(Duration.ofDays(7)), now, 500).stream()
            .filter(row -> row.id() == note)
            .findFirst()
            .orElseThrow();

    assertThat(pooled.likes()).isEqualTo(5);
    assertThat(pooled.quotes()).isEqualTo(2);
    assertThat(pooled.highlights()).isEqualTo(1);
    assertThat(pooled.tags()).containsExactly("Java");
    assertThat(pooled.userId()).isEqualTo(writer);
    assertThat(pooledNote.likes()).isEqualTo(1);
    assertThat(pooledNote.tags()).containsExactly("java");
    assertThat(pooledNote.userId()).isEqualTo(writer);
  }

  @Test
  void theReaderFollowsMembersAndAcceptedAccountsElsewhere() {
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        reader,
        writer);
    long accepted = actor("ac");
    long pending = actor("pe");
    jdbc.update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6)),"
            + " (?, ?, ?, NULL, NOW(6), NOW(6))",
        reader,
        accepted,
        "https://kurl.me/follow/ac" + reader,
        reader,
        pending,
        "https://kurl.me/follow/pe" + reader);

    assertThat(signals.follows(reader)).isEqualTo(Set.of("u" + writer, "r" + accepted));
    assertThat(signals.follows(writer)).isEmpty();
  }

  private HomeFeedQueryService.Page forYou(String cursor, int size, boolean hide) {
    return home.page(ask(reader, viewer, "for-you", cursor, null, size, hide));
  }

  private List<String> keys(HomeFeedQueryService.Page page) {
    List<String> keys = new ArrayList<>();
    for (HomeFeedQueryService.Item item : page.items()) {
      if (item.kind().equals("note")) {
        NoteView note = item.note();
        if (ours(note.author().id())) {
          keys.add("note:" + note.id());
        }
      } else if (item.kind().equals("post")) {
        PostCard card = item.post();
        if (ours(card.item().author().id())) {
          keys.add("post:" + card.item().id());
        }
      }
    }
    return keys;
  }

  private boolean ours(Long author) {
    return author != null && (author == reader || author == writer || author == stranger);
  }

  private Instant ago(long minutes) {
    return now.minus(Duration.ofMinutes(minutes));
  }

  private static LocalDateTime utc(Instant at) {
    return LocalDateTime.ofInstant(at, ZoneOffset.UTC);
  }

  private void stored(int kind, long id, Instant at) {
    jdbc.update(
        "INSERT INTO local_seen (viewer_kind, viewer_key, item_kind, item_id, seen_at)"
            + " VALUES (?, ?, ?, ?, ?)",
        viewer.kind(),
        viewer.keyBytes(),
        kind,
        id,
        utc(at));
  }

  private long member(String prefix) {
    String handle = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity(handle + "@example.com", "google", "g-" + handle);
    user.claimUsername(handle);
    return users.save(user).getId();
  }

  private long actor(String name) {
    String handle = name + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String uri = "https://" + handle + ".example/users/" + handle;
    jdbc.update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?, ?, 'pem', ?, ?, ?, NOW(6), NOW(6), NOW(6))",
        uri,
        uri + "#main-key",
        uri + "/inbox",
        handle,
        handle + ".example");
    return jdbc.queryForObject(
        "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, uri);
  }

  private long room(long owner, long member) {
    jdbc.update(
        "INSERT INTO room (owner_id, name, approval_required, created_at, updated_at)"
            + " VALUES (?, 'for you', FALSE, NOW(6), NOW(6))",
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

  private long note(long author, String visibility, Long room, Long replyTo, Instant at) {
    jdbc.update(
        "INSERT INTO note (user_id, body, visibility, room_id, in_reply_to_id, reply, created_at)"
            + " VALUES (?, 'here', ?, ?, ?, ?, ?)",
        author,
        visibility,
        room,
        replyTo,
        replyTo != null,
        utc(at));
    return jdbc.queryForObject("SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, author);
  }

  private long post(long author, String status, String audience, Instant at) {
    String slug = "fy-" + UUID.randomUUID().toString().substring(0, 8);
    jdbc.update(
        "INSERT INTO posts (user_id, slug, title, created_at, updated_at, status, audience,"
            + " published_at, body_text_length, language_tag)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 500, 'ko')",
        author,
        slug,
        slug,
        utc(at),
        utc(at),
        status,
        audience,
        status.equals("PUBLISHED") ? utc(at) : null);
    return jdbc.queryForObject("SELECT id FROM posts WHERE slug = ?", Long.class, slug);
  }
}
