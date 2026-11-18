package com.example.short_link.note.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.note.NoteTopicReader;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteFeedReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class NoteTopicKeptIntegrationTest {

  @Autowired private NoteTopicReader topics;
  @Autowired private NoteFeedReader feeds;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private String tag;
  private String url;
  private Long blocker;
  private Long member;
  private Long alice;
  private Long bob;

  @BeforeEach
  void peopleHereAndElsewhere() {
    tag = "k" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    url = "https://news-" + tag + ".example/a";
    blocker = user("b");
    member = user("m");
    alice = actor("alice");
    bob = actor("bob");
    update(
        "INSERT INTO federation_remote_block (user_id, remote_actor_id, created_at)"
            + " VALUES (?1, ?2, NOW(6))",
        blocker,
        bob);
  }

  private Long user(String prefix) {
    String name = prefix + tag;
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private Long actor(String username) {
    String uri = "https://" + username + "-" + tag + ".example/users/" + username;
    update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?1, ?2, 'pem', ?3, ?4, ?5, NOW(6), NOW(6), NOW(6))",
        uri,
        uri + "#main-key",
        uri + "/inbox",
        username + tag,
        username + "-" + tag + ".example");
    return number("SELECT id FROM federation_remote_actor WHERE actor_uri = ?1", uri);
  }

  private void update(String sql, Object... values) {
    var query = em.createNativeQuery(sql);
    for (int i = 0; i < values.length; i++) {
      query.setParameter(i + 1, values[i]);
    }
    query.executeUpdate();
  }

  private Long number(String sql, Object... values) {
    var query = em.createNativeQuery(sql);
    for (int i = 0; i < values.length; i++) {
      query.setParameter(i + 1, values[i]);
    }
    return ((Number) query.getSingleResult()).longValue();
  }

  private String uriOf(Long noteId) {
    return (String)
        em.createNativeQuery("SELECT uri FROM note WHERE id = ?1")
            .setParameter(1, noteId)
            .getSingleResult();
  }

  private Long remoteNote(Long actorId, String visibility, Instant createdAt) {
    String uri = "https://elsewhere-" + tag + ".example/notes/" + UUID.randomUUID();
    update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, visibility)"
            + " VALUES (?1, ?2, 'hi', ?3, ?4)",
        actorId,
        uri,
        createdAt,
        visibility);
    return number("SELECT id FROM note WHERE uri = ?1", uri);
  }

  private Long localNote(Long userId) {
    update("INSERT INTO note (user_id, body, created_at) VALUES (?1, 'from here', NOW(6))", userId);
    return number("SELECT MAX(id) FROM note WHERE user_id = ?1", userId);
  }

  private void carries(Long noteId, boolean tagged, boolean linked) {
    if (tagged) {
      update("INSERT INTO note_tag (note_id, tag) VALUES (?1, ?2)", noteId, tag);
    }
    if (linked) {
      update(
          "INSERT INTO note_link_preview (note_id, url, title, fetched_at)"
              + " VALUES (?1, ?2, 'A headline', NOW(6))",
          noteId,
          url);
    }
  }

  private NoteTopicReader.Carrying byTag() {
    return new NoteTopicReader.Carrying(tag, null);
  }

  private NoteTopicReader.Carrying byLink() {
    return new NoteTopicReader.Carrying(null, url);
  }

  private static List<Long> ids(List<NoteTopicReader.Kept> rows) {
    return rows.stream().map(NoteTopicReader.Kept::id).toList();
  }

  private static List<Long> listed(List<NoteEntity> page) {
    return page.stream().map(NoteEntity::getId).toList();
  }

  @Test
  void keptNotesUnderATagOrALinkPageDownByIdWithTheirAddressAndTime() {
    Instant written = Instant.parse("2026-11-01T03:04:05Z");
    List<Long> all = new ArrayList<>();
    for (int n = 0; n < 5; n++) {
      Long note = remoteNote(alice, "PUBLIC", written.plusSeconds(n));
      carries(note, true, true);
      all.add(note);
    }
    Long here = localNote(member);
    carries(here, true, true);
    all.add(here);
    carries(remoteNote(alice, "PUBLIC", written), false, false);
    List<Long> newestFirst = all.reversed();

    for (NoteTopicReader.Carrying what : List.of(byTag(), byLink())) {
      List<NoteTopicReader.Kept> top = topics.kept(what, null, null, 4);
      List<NoteTopicReader.Kept> rest = topics.kept(what, null, top.getLast().id(), 4);

      assertThat(ids(top)).containsExactlyElementsOf(newestFirst.subList(0, 4));
      assertThat(ids(rest)).containsExactlyElementsOf(newestFirst.subList(4, 6));
      assertThat(top.getFirst().uri()).isNull();
      assertThat(top.get(1).uri()).isEqualTo(uriOf(all.get(4)));
      assertThat(top.get(1).createdAt()).isEqualTo(written.plusSeconds(4));
      assertThat(topics.kept(what, null, all.getFirst(), 4)).isEmpty();
    }
  }

  @Test
  void aKeptNoteIsListedForExactlyThoseThePublicTagAndLinkPagesListItFor() {
    Long shown = remoteNote(alice, "PUBLIC", Instant.now());
    Long blocked = remoteNote(bob, "PUBLIC", Instant.now());
    Long followersOnly = remoteNote(alice, "PRIVATE", Instant.now());
    Long here = localNote(member);
    for (Long note : List.of(shown, blocked, followersOnly, here)) {
      carries(note, true, true);
    }

    for (Long viewer : new Long[] {null, member, blocker}) {
      assertThat(ids(topics.kept(byTag(), viewer, null, 50)))
          .containsExactlyElementsOf(listed(feeds.tagged(tag, viewer, 0, 50)));
      assertThat(ids(topics.kept(byLink(), viewer, null, 50)))
          .containsExactlyElementsOf(listed(feeds.linked(url, viewer, 0, 50)));
    }
    assertThat(ids(topics.kept(byTag(), null, null, 50))).containsExactly(here, blocked, shown);
    assertThat(ids(topics.kept(byTag(), blocker, null, 50))).containsExactly(here, shown);
    assertThat(ids(topics.kept(byLink(), blocker, null, 50))).containsExactly(here, shown);
  }

  @Test
  void ofSomeAddressesOnlyTheKeptNotesTheViewerIsShownUnderThatTagOrLinkAreNamed() {
    Long shown = remoteNote(alice, "PUBLIC", Instant.now());
    Long blocked = remoteNote(bob, "PUBLIC", Instant.now());
    Long followersOnly = remoteNote(alice, "PRIVATE", Instant.now());
    Long untagged = remoteNote(alice, "PUBLIC", Instant.now());
    Long notAsked = remoteNote(alice, "PUBLIC", Instant.now());
    for (Long note : List.of(shown, blocked, followersOnly, notAsked)) {
      carries(note, true, true);
    }
    List<String> asked =
        List.of(
            uriOf(shown),
            uriOf(blocked),
            uriOf(followersOnly),
            uriOf(untagged),
            "https://elsewhere-" + tag + ".example/notes/never-kept");

    for (NoteTopicReader.Carrying what : List.of(byTag(), byLink())) {
      assertThat(topics.keptAmong(what, null, asked))
          .containsExactlyInAnyOrder(uriOf(shown), uriOf(blocked));
      assertThat(topics.keptAmong(what, blocker, asked)).containsExactly(uriOf(shown));
    }
  }

  @Test
  void twoAccountsUsingATagOrALinkInTwoDaysMakeItTrendHere() {
    Long second = user("s");
    for (Long author : List.of(member, second)) {
      carries(localNote(author), true, true);
    }
    Instant now = Instant.now();

    assertThat(topics.trendingTags(now, 2, 2, 50)).contains(new NoteTopicReader.Tag(tag, 2));
    assertThat(topics.trendingLinks(now, 2, 2, 50))
        .contains(new NoteTopicReader.Link(url, "A headline", null, 2));
    assertThat(topics.trendingTags(now, 2, 3, 50))
        .extracting(NoteTopicReader.Tag::name)
        .doesNotContain(tag);
  }
}
