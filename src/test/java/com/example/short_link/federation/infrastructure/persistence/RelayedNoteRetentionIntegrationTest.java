package com.example.short_link.federation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class RelayedNoteRetentionIntegrationTest {

  private static final Instant OLD = Instant.parse("2001-01-01T00:00:00Z");
  private static final Instant BEFORE = Instant.parse("2001-01-08T00:00:00Z");

  @Autowired private FederationRelayedNoteRepository relayed;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private String tag;
  private Long member;
  private Long alice;
  private Long bob;
  private Long carol;

  @BeforeEach
  void peopleHereAndElsewhere() {
    tag = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    UserEntity user = new UserEntity(tag + "@example.com", "google", tag);
    user.claimUsername(tag);
    member = users.save(user).getId();
    alice = actor("alice");
    bob = actor("bob");
    carol = actor("carol");
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
        username,
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
    Object value = query.getSingleResult();
    return value == null ? null : ((Number) value).longValue();
  }

  private Long remoteNote(Long actorId) {
    String uri = "https://elsewhere-" + tag + ".example/notes/" + UUID.randomUUID();
    update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at) VALUES (?1, ?2, 'hi', ?3)",
        actorId,
        uri,
        OLD);
    return number("SELECT id FROM note WHERE uri = ?1", uri);
  }

  private Long relayedNote(Long actorId, Instant receivedAt) {
    Long note = remoteNote(actorId);
    relayed.mark(note, null, receivedAt);
    return note;
  }

  private Long localNote(String column, Long target) {
    update(
        "INSERT INTO note (user_id, body, created_at, "
            + column
            + ") VALUES (?1, 'mine', NOW(6), ?2)",
        member,
        target);
    return number("SELECT MAX(id) FROM note WHERE user_id = ?1", member);
  }

  private void follow(Long actorId, boolean accepted) {
    update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?1, ?2, ?3, ?4, NOW(6), NOW(6))",
        member,
        actorId,
        "https://kurl.me/ap/actors/x#follows/" + UUID.randomUUID(),
        accepted ? OLD : null);
  }

  private List<Long> left(Long... notes) {
    return List.of(notes).stream()
        .filter(note -> number("SELECT COUNT(*) FROM note WHERE id = ?1", note) > 0)
        .toList();
  }

  @Test
  void aNoteARelayBroughtGoesAfterItsTimeUnlessAMemberTouchedItOrFollowsItsAuthor() {
    Long untouched = relayedNote(alice, OLD);
    Long recent = relayedNote(alice, BEFORE.plusSeconds(1));
    Long notRelayed = remoteNote(alice);

    Long liked = relayedNote(alice, OLD);
    update(
        "INSERT INTO note_like (note_id, user_id, created_at) VALUES (?1, ?2, NOW(6))",
        liked,
        member);
    Long reposted = relayedNote(alice, OLD);
    update(
        "INSERT INTO note_repost (note_id, user_id, created_at) VALUES (?1, ?2, NOW(6))",
        reposted,
        member);
    Long bookmarked = relayedNote(alice, OLD);
    update(
        "INSERT INTO note_bookmark (note_id, user_id, created_at) VALUES (?1, ?2, NOW(6))",
        bookmarked,
        member);
    Long voted = relayedNote(alice, OLD);
    update(
        "INSERT INTO note_poll_vote (note_id, user_id, choices, created_at)"
            + " VALUES (?1, ?2, 1, NOW(6))",
        voted,
        member);
    Long answered = relayedNote(alice, OLD);
    localNote("in_reply_to_id", answered);
    Long quoted = relayedNote(alice, OLD);
    localNote("quoted_note_id", quoted);
    Long quotedInAPost = relayedNote(alice, OLD);
    update(
        "INSERT INTO posts (user_id, slug, title, status, created_at, updated_at)"
            + " VALUES (?1, ?2, 'quote', 'DRAFT', NOW(6), NOW(6))",
        member,
        "q-" + tag);
    update(
        "INSERT INTO post_note_quote (post_id, note_id) VALUES (?1, ?2)",
        number("SELECT id FROM posts WHERE slug = ?1", "q-" + tag),
        quotedInAPost);
    follow(bob, true);
    Long byAFollowedAuthor = relayedNote(bob, OLD);

    follow(carol, false);
    Long byARequestedAuthor = relayedNote(carol, OLD);
    Long answeredElsewhere = relayedNote(alice, OLD);
    update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, in_reply_to_id)"
            + " VALUES (?1, ?2, 'reply', NOW(6), ?3)",
        carol,
        "https://carol-" + tag + ".example/notes/reply",
        answeredElsewhere);
    Long reply =
        number(
            "SELECT id FROM note WHERE uri = ?1", "https://carol-" + tag + ".example/notes/reply");
    Long quotedElsewhere = relayedNote(alice, OLD);
    update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, quoted_note_id)"
            + " VALUES (?1, ?2, 'quote', NOW(6), ?3)",
        carol,
        "https://carol-" + tag + ".example/notes/quote",
        quotedElsewhere);
    Long likedElsewhere = relayedNote(alice, OLD);
    update(
        "INSERT INTO note_remote_reaction (note_id, remote_actor_id, kind, activity_id,"
            + " created_at) VALUES (?1, ?2, 'LIKE', ?3, NOW(6))",
        likedElsewhere,
        carol,
        "https://carol-" + tag + ".example/likes/1");
    update(
        "INSERT INTO note_media (note_id, position, storage_key, url, content_type, created_at)"
            + " VALUES (?1, 0, '', 'https://alice.example/a.png', 'image/png', NOW(6))",
        untouched);
    update("INSERT INTO note_tag (note_id, tag) VALUES (?1, ?2)", untouched, tag);

    List<Long> expired = relayed.expired(BEFORE, 200);
    assertThat(expired)
        .containsExactly(
            untouched, byARequestedAuthor, answeredElsewhere, quotedElsewhere, likedElsewhere);
    assertThat(relayed.deleteUntouched(expired)).isEqualTo(5);

    assertThat(
            left(
                untouched,
                recent,
                notRelayed,
                liked,
                reposted,
                bookmarked,
                voted,
                answered,
                quoted,
                quotedInAPost,
                byAFollowedAuthor,
                byARequestedAuthor,
                answeredElsewhere,
                quotedElsewhere,
                likedElsewhere))
        .containsExactly(
            recent,
            notRelayed,
            liked,
            reposted,
            bookmarked,
            voted,
            answered,
            quoted,
            quotedInAPost,
            byAFollowedAuthor);
    assertThat(number("SELECT COUNT(*) FROM note_media WHERE note_id = ?1", untouched)).isZero();
    assertThat(number("SELECT COUNT(*) FROM note_tag WHERE note_id = ?1", untouched)).isZero();
    assertThat(number("SELECT COUNT(*) FROM federation_relayed_note WHERE note_id = ?1", untouched))
        .isZero();
    assertThat(number("SELECT in_reply_to_id FROM note WHERE id = ?1", reply)).isNull();
  }

  @Test
  void oneBatchTakesAtMostItsLimitOldestFirst() {
    Long third = relayedNote(alice, OLD.plusSeconds(2));
    Long first = relayedNote(alice, OLD);
    Long second = relayedNote(alice, OLD.plusSeconds(1));

    assertThat(relayed.expired(BEFORE, 2)).containsExactly(first, second);
    assertThat(relayed.deleteUntouched(List.of(first, second))).isEqualTo(2);
    assertThat(left(first, second, third)).containsExactly(third);
  }

  @Test
  void aTouchThatCameAfterTheReadStillKeepsItsNote() {
    Long liked = relayedNote(alice, OLD);
    Long answered = relayedNote(alice, OLD.plusSeconds(1));
    Long quoted = relayedNote(alice, OLD.plusSeconds(2));
    Long byAFollowedAuthor = relayedNote(bob, OLD.plusSeconds(3));
    Long untouched = relayedNote(alice, OLD.plusSeconds(4));
    List<Long> read = relayed.expired(BEFORE, 200);
    assertThat(read).contains(liked, answered, quoted, byAFollowedAuthor, untouched);

    update(
        "INSERT INTO note_like (note_id, user_id, created_at) VALUES (?1, ?2, NOW(6))",
        liked,
        member);
    localNote("in_reply_to_id", answered);
    localNote("quoted_note_id", quoted);
    follow(bob, true);

    assertThat(
            relayed.deleteUntouched(List.of(liked, answered, quoted, byAFollowedAuthor, untouched)))
        .isEqualTo(1);
    assertThat(left(liked, answered, quoted, byAFollowedAuthor, untouched))
        .containsExactly(liked, answered, quoted, byAFollowedAuthor);
  }

  @Test
  void keptNotesAreCountedByTheRelayThatBroughtThemAndByTheDay() {
    update(
        "INSERT INTO federation_relay (actor_uri, inbox, follow_activity_id, state, created_at,"
            + " updated_at) VALUES (?1, ?1, ?2, 'ACCEPTED', NOW(6), NOW(6))",
        "https://relay-" + tag + ".example/tag/dev",
        "https://kurl.me/ap/instance#relays/" + tag);
    Long relay =
        number(
            "SELECT id FROM federation_relay WHERE actor_uri = ?1",
            "https://relay-" + tag + ".example/tag/dev");
    Map<Long, Long> before = relayed.countByRelay();
    long since = relayed.receivedSince(BEFORE);

    relayed.mark(remoteNote(alice), relay, BEFORE);
    relayed.mark(remoteNote(alice), relay, OLD);
    relayed.mark(remoteNote(alice), null, BEFORE.plusSeconds(1));

    assertThat(relayed.countByRelay()).containsEntry(relay, 2L);
    assertThat(relayed.countByRelay().get(0L)).isEqualTo(before.getOrDefault(0L, 0L) + 1);
    assertThat(relayed.receivedSince(BEFORE)).isEqualTo(since + 2);
  }
}
