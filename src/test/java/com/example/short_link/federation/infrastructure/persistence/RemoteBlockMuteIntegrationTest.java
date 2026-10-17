package com.example.short_link.federation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteAccountView;
import com.example.short_link.federation.application.RemoteBlocks;
import com.example.short_link.federation.application.RemoteMutes;
import com.example.short_link.federation.domain.RemoteAccountStanding;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.AccountExportReader;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
class RemoteBlockMuteIntegrationTest {

  @Autowired private NoteRepository notes;
  @Autowired private NoteRepostRepository reposts;
  @Autowired private NotePeopleReader people;
  @Autowired private MuteRepository userMutes;
  @Autowired private NotificationFollowerReader sharers;
  @Autowired private RemoteAccountStandingReader standings;
  @Autowired private RemoteBlocks remoteBlocks;
  @Autowired private FederationUrls urls;
  @Autowired private RemoteMutes remoteMutes;
  @Autowired private UserRepository users;
  @Autowired private AccountExportReader exports;
  @Autowired private EntityManager em;

  private String tag;
  private Long blocker;
  private Long muter;
  private Long stranger;
  private Long friend;
  private Long alice;
  private Long bob;

  @BeforeEach
  void peopleHereAndElsewhere() {
    tag = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    blocker = user("b");
    muter = user("m");
    stranger = user("s");
    friend = user("f");
    alice = actor("alice");
    bob = actor("bob");
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
    return id("SELECT id FROM federation_remote_actor WHERE actor_uri = ?1", uri);
  }

  private void update(String sql, Object... values) {
    var query = em.createNativeQuery(sql);
    for (int i = 0; i < values.length; i++) {
      query.setParameter(i + 1, values[i]);
    }
    query.executeUpdate();
  }

  private Long id(String sql, Object... values) {
    var query = em.createNativeQuery(sql);
    for (int i = 0; i < values.length; i++) {
      query.setParameter(i + 1, values[i]);
    }
    return ((Number) query.getSingleResult()).longValue();
  }

  private long count(String sql, Object... values) {
    return id(sql, values);
  }

  private Long remoteNote(Long actorId, String visibility) {
    String uri = "https://elsewhere-" + tag + ".example/notes/" + UUID.randomUUID();
    update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, visibility)"
            + " VALUES (?1, ?2, 'from elsewhere #"
            + tag
            + "', NOW(6), ?3)",
        actorId,
        uri,
        visibility);
    return id("SELECT id FROM note WHERE uri = ?1", uri);
  }

  private Long localNote(Long userId) {
    update("INSERT INTO note (user_id, body, created_at) VALUES (?1, 'from here', NOW(6))", userId);
    return id("SELECT MAX(id) FROM note WHERE user_id = ?1", userId);
  }

  private void block(Long userId, Long actorId) {
    update(
        "INSERT INTO federation_remote_block (user_id, remote_actor_id, created_at)"
            + " VALUES (?1, ?2, NOW(6))",
        userId,
        actorId);
  }

  private void mute(Long userId, Long actorId, boolean notifications, Instant expiresAt) {
    update(
        "INSERT INTO federation_remote_mute (user_id, remote_actor_id, hide_notifications,"
            + " expires_at, created_at) VALUES (?1, ?2, ?3, ?4, NOW(6))",
        userId,
        actorId,
        notifications,
        expiresAt);
  }

  private void follow(Long userId, Long actorId) {
    update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?1, ?2, ?3, NOW(6), NOW(6), NOW(6))",
        userId,
        actorId,
        "https://kurl.me/ap/actors/x#follows/" + UUID.randomUUID());
  }

  private void repost(Long userId, Long noteId) {
    update(
        "INSERT INTO note_repost (note_id, user_id, created_at) VALUES (?1, ?2, NOW(6))",
        noteId,
        userId);
  }

  private static List<Long> ids(List<NoteEntity> page) {
    return page.stream().map(NoteEntity::getId).toList();
  }

  private static Instant past() {
    return Instant.now().minusSeconds(60);
  }

  private static Instant future() {
    return Instant.now().plusSeconds(3600);
  }

  @Test
  void aBlockOrAMuteTakesTheAccountOutOfTheFederatedFeedTagsLinksAndTrends() {
    Long theirs = remoteNote(alice, "PUBLIC");
    Long control = remoteNote(bob, "PUBLIC");
    for (Long note : List.of(theirs, control)) {
      update("INSERT INTO note_tag (note_id, tag) VALUES (?1, ?2)", note, tag);
      update(
          "INSERT INTO note_link_preview (note_id, url, fetched_at) VALUES (?1, ?2, NOW(6))",
          note,
          "https://news-" + tag + ".example/a");
      update(
          "INSERT INTO note_like (note_id, user_id, created_at) VALUES (?1, ?2, NOW(6))",
          note,
          friend);
    }
    block(blocker, alice);
    mute(muter, alice, false, future());
    mute(stranger, alice, true, past());
    String url = "https://news-" + tag + ".example/a";

    for (Long viewer : List.of(blocker, muter)) {
      assertThat(ids(notes.federated(viewer, 0, 50))).contains(control).doesNotContain(theirs);
      assertThat(ids(notes.tagged(tag, viewer, 0, 50))).containsExactly(control);
      assertThat(ids(notes.linked(url, viewer, 0, 50))).containsExactly(control);
      assertThat(ids(notes.trending(viewer, 0, 50))).contains(control).doesNotContain(theirs);
    }
    assertThat(ids(notes.federated(stranger, 0, 50))).contains(theirs, control);
    assertThat(ids(notes.tagged(tag, stranger, 0, 50))).containsExactly(control, theirs);
    assertThat(ids(notes.linked(url, null, 0, 50))).containsExactly(control, theirs);
    assertThat(ids(notes.trending(stranger, 0, 50))).contains(theirs, control);
  }

  @Test
  void aBlockHoldsEvenWhereTheAccountIsOpenedDirectlyAndAMuteDoesNot() {
    Long theirs = remoteNote(alice, "PUBLIC");
    block(blocker, alice);
    mute(muter, alice, true, null);

    assertThat(notes.findUnblocked(theirs, blocker)).isEmpty();
    assertThat(notes.findUnblocked(theirs, muter)).isPresent();
    assertThat(ids(notes.byRemoteActor(alice, blocker, 0, 50))).isEmpty();
    assertThat(ids(notes.byRemoteActor(alice, muter, 0, 50))).containsExactly(theirs);
    assertThat(people.remoteAuthors(Set.of(alice, bob), blocker)).containsOnlyKeys(bob);
    assertThat(people.remoteAuthors(Set.of(alice, bob), muter)).containsOnlyKeys(alice, bob);
    assertThat(people.remoteAuthors(Set.of(alice, bob), null)).containsOnlyKeys(alice, bob);
  }

  @Test
  void repliesQuotesAndReplyContextsFromABlockedOrMutedAccountLeaveTheThread() {
    Long mine = localNote(friend);
    Long reply = remoteNote(alice, "PUBLIC");
    Long hidden = remoteNote(alice, "PUBLIC");
    Long quote = remoteNote(alice, "PUBLIC");
    Long others = remoteNote(bob, "PUBLIC");
    update(
        "UPDATE note SET in_reply_to_id = ?1, reply = TRUE WHERE id IN (?2, ?3)",
        mine,
        reply,
        others);
    update(
        "UPDATE note SET in_reply_to_id = ?1, reply = TRUE, reply_hidden_at = NOW(6) WHERE id = ?2",
        mine,
        hidden);
    update(
        "INSERT INTO posts (user_id, slug, title, created_at, updated_at)"
            + " VALUES (?1, ?2, 'a post', NOW(6), NOW(6))",
        friend,
        "p" + tag);
    Long post = id("SELECT id FROM posts WHERE slug = ?1", "p" + tag);
    update(
        "UPDATE note SET quoted_note_id = ?1, quoted_post_id = ?2 WHERE id = ?3",
        mine,
        post,
        quote);
    block(blocker, alice);
    mute(muter, alice, true, null);

    for (Long viewer : List.of(blocker, muter)) {
      assertThat(ids(notes.replies(mine, viewer, 50))).containsExactly(others);
      assertThat(ids(notes.hiddenReplies(mine, viewer, 50))).isEmpty();
      assertThat(ids(notes.quotesOf(mine, viewer, 0, 50))).isEmpty();
      assertThat(ids(notes.quotesOfPost(post, viewer, 0, 50))).isEmpty();
      assertThat(notes.countQuotesOfPost(post, viewer)).isZero();
      assertThat(ids(notes.heardAmong(List.of(reply, others), viewer))).containsExactly(others);
    }
    assertThat(ids(notes.replies(mine, stranger, 50))).containsExactly(reply, others);
    assertThat(ids(notes.hiddenReplies(mine, stranger, 50))).containsExactly(hidden);
    assertThat(ids(notes.quotesOf(mine, stranger, 0, 50))).containsExactly(quote);
    assertThat(ids(notes.quotesOfPost(post, stranger, 0, 50))).containsExactly(quote);
    assertThat(notes.countQuotesOfPost(post, stranger)).isEqualTo(1);
  }

  @Test
  void theFollowingFeedDropsABlockedAccountsRepostedAndTaggedNotesAndAMutedFollow() {
    Long reposted = remoteNote(alice, "PUBLIC");
    Long tagged = remoteNote(alice, "PUBLIC");
    Long followed = remoteNote(alice, "PUBLIC");
    Long control = remoteNote(bob, "PUBLIC");
    update("INSERT INTO note_tag (note_id, tag) VALUES (?1, ?2)", tagged, tag);
    repost(friend, reposted);
    repost(friend, control);
    for (Long viewer : List.of(blocker, muter, stranger)) {
      update(
          "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?1, ?2, NOW(6))",
          viewer,
          friend);
      update(
          "INSERT INTO user_tag_pref (user_id, tag, kind, created_at)"
              + " VALUES (?1, ?2, 'FOLLOW', NOW(6))",
          viewer,
          tag);
    }
    follow(muter, alice);
    follow(stranger, alice);
    block(blocker, alice);
    mute(muter, alice, false, null);

    assertThat(feed(blocker)).contains(control).doesNotContain(reposted, tagged, followed);
    assertThat(feed(muter)).contains(control).doesNotContain(reposted, tagged, followed);
    assertThat(feed(stranger)).contains(control, reposted, tagged, followed);
  }

  private List<Long> feed(Long viewer) {
    return notes.following(List.of(viewer, friend), viewer, 0, 50).stream()
        .map(NoteFeedRow::note)
        .map(NoteEntity::getId)
        .toList();
  }

  @Test
  void aDirectNoteFromABlockedAccountLeavesPrivateMentions() {
    Long direct = remoteNote(alice, "DIRECT");
    for (Long viewer : List.of(blocker, muter)) {
      update("INSERT INTO note_recipient (note_id, user_id) VALUES (?1, ?2)", direct, viewer);
    }
    block(blocker, alice);
    mute(muter, alice, true, null);

    assertThat(ids(notes.direct(blocker, 0, 50))).isEmpty();
    assertThat(ids(notes.direct(muter, 0, 50))).containsExactly(direct);
  }

  @Test
  void aMembersRepostsTabLeavesOutABlockedOrMutedAccount() {
    Long theirs = remoteNote(alice, "PUBLIC");
    Long control = remoteNote(bob, "PUBLIC");
    repost(friend, theirs);
    repost(friend, control);
    block(blocker, alice);
    mute(muter, alice, false, future());
    mute(stranger, alice, false, past());

    assertThat(reposts.recentNoteIdsByUser(friend, blocker, 0, 50)).containsExactly(control);
    assertThat(reposts.recentNoteIdsByUser(friend, muter, 0, 50)).containsExactly(control);
    assertThat(reposts.recentNoteIdsByUser(friend, stranger, 0, 50))
        .containsExactly(control, theirs);
  }

  @Test
  void noticesFromABlockedAccountOrOneMutedWithItsNoticesAreSilenced() {
    Long quiet = user("q");
    Long expired = user("e");
    block(blocker, alice);
    mute(muter, alice, true, null);
    mute(quiet, alice, false, null);
    mute(expired, alice, true, past());
    Instant now = Instant.now();

    assertThat(userMutes.silences(blocker, null, alice, null, now)).isTrue();
    assertThat(userMutes.silences(muter, null, alice, null, now)).isTrue();
    assertThat(userMutes.silences(quiet, null, alice, null, now)).isFalse();
    assertThat(userMutes.silences(expired, null, alice, null, now)).isFalse();
    assertThat(userMutes.silences(blocker, null, bob, null, now)).isFalse();

    Long theirs = remoteNote(alice, "PUBLIC");
    for (Long sharer : List.of(blocker, muter, quiet, expired, stranger)) {
      repost(sharer, theirs);
    }
    assertThat(sharers.noteSharersOf(theirs, null, alice))
        .containsExactlyInAnyOrder(quiet, expired, stranger);
  }

  @Test
  void theStandingOfEachAccountComesInOneRead() {
    Long carol = actor("carol");
    block(blocker, alice);
    mute(blocker, bob, true, future());
    mute(blocker, carol, true, past());
    update(
        "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?1, ?2, NOW(6))",
        blocker,
        "carol-" + tag + ".example");

    Map<Long, RemoteAccountStanding> found =
        standings.of(blocker, List.of(alice, bob, carol), Instant.now());

    assertThat(found)
        .containsEntry(alice, new RemoteAccountStanding(false, true, false))
        .containsEntry(bob, new RemoteAccountStanding(false, false, true))
        .containsEntry(carol, new RemoteAccountStanding(true, false, false));
    assertThat(standings.of(stranger, List.of(alice), Instant.now()))
        .containsEntry(alice, RemoteAccountStanding.NONE);
    assertThat(standings.of(null, List.of(alice), Instant.now())).isEmpty();
    assertThat(standings.of(blocker, List.of(), Instant.now())).isEmpty();
  }

  @Test
  void whatAMemberHidFromTrendsAndWhoIsKnownHereComeInOneReadEach() {
    Long carol = actor("carol");
    block(blocker, alice);
    mute(blocker, bob, false, future());
    mute(blocker, carol, false, past());
    String unknown = "dave-" + tag + ".example";
    update(
        "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?1, ?2, NOW(6))",
        blocker,
        unknown);
    update(
        "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?1, ?2, NOW(6))",
        blocker,
        "elsewhere-" + tag + ".example");
    List<String> accounts =
        List.of("alice", "bob", "carol").stream()
            .map(name -> "https://" + name + "-" + tag + ".example/users/" + name)
            .toList();
    List<String> servers =
        List.of("alice-" + tag + ".example", "carol-" + tag + ".example", unknown);

    RemoteAccountStandingReader.Hidden hidden =
        standings.hidden(blocker, accounts, servers, Instant.now());

    assertThat(hidden.actorUris()).containsExactlyInAnyOrder(accounts.get(0), accounts.get(1));
    assertThat(hidden.domains()).containsExactly(unknown);
    assertThat(standings.hidden(stranger, accounts, servers, Instant.now()))
        .isEqualTo(new RemoteAccountStandingReader.Hidden(Set.of(), Set.of()));
    assertThat(standings.hidden(null, accounts, servers, Instant.now()))
        .isSameAs(RemoteAccountStandingReader.Hidden.NONE);
    assertThat(standings.hidden(blocker, List.of(), List.of(), Instant.now()))
        .isSameAs(RemoteAccountStandingReader.Hidden.NONE);
    assertThat(standings.idsOf(List.of(accounts.get(0), accounts.get(2), "https://" + unknown)))
        .isEqualTo(Map.of(accounts.get(0), alice, accounts.get(2), carol));
    assertThat(standings.idsOf(List.of())).isEmpty();
  }

  @Test
  void blockingEndsFollowsBothWaysAndTellsTheirServerThenUnblockingUndoesIt() {
    String me = urls.actor("p" + tag);
    update(
        "INSERT INTO federation_actor (user_id, public_id, public_key_pem, private_key_cipher,"
            + " created_at, updated_at) VALUES (?1, ?2, 'pem', 'cipher', NOW(6), NOW(6))",
        blocker,
        "p" + tag);
    follow(blocker, alice);
    update(
        "INSERT INTO federation_follower (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?1, ?2, ?3, NOW(6), NOW(6), NOW(6))",
        blocker,
        alice,
        "https://alice-" + tag + ".example/follows/1");

    var blocked = remoteBlocks.block(blocker, alice);

    assertThat(blocked.blocking()).isTrue();
    assertThat(blocked.following()).isFalse();
    assertThat(count("SELECT COUNT(*) FROM federation_following WHERE user_id = ?1", blocker))
        .isZero();
    assertThat(count("SELECT COUNT(*) FROM federation_follower WHERE user_id = ?1", blocker))
        .isZero();
    assertThat(sent(blocker)).containsExactly("Undo", "Reject", "Block");
    assertThat(
            em.createNativeQuery(
                    "SELECT activity_id FROM federation_remote_block WHERE user_id = ?1")
                .setParameter(1, blocker)
                .getSingleResult()
                .toString())
        .startsWith(me + "#blocks/");
    assertThat(remoteBlocks.blocks(blocker, 0, 20))
        .extracting(RemoteAccountView::id)
        .containsExactly(alice);

    var unblocked = remoteBlocks.unblock(blocker, alice);

    assertThat(unblocked.blocking()).isFalse();
    assertThat(sent(blocker)).containsExactly("Undo", "Reject", "Block", "Undo");
    assertThat(remoteBlocks.blocks(blocker, 0, 20)).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private List<String> sent(Long userId) {
    return ((List<Object>)
            em.createNativeQuery(
                    "SELECT JSON_UNQUOTE(JSON_EXTRACT(body, '$.type')) FROM federation_delivery"
                        + " WHERE signer_user_id = ?1 ORDER BY id")
                .setParameter(1, userId)
                .getResultList())
        .stream().map(Object::toString).toList();
  }

  @Test
  void aMuteAnswersMutedAtOnceListsUntilItEndsAndUnmutingClearsIt() {
    follow(muter, alice);

    var muted = remoteMutes.mute(muter, alice, false, 3600L);

    assertThat(muted.muting()).isTrue();
    assertThat(muted.following()).isTrue();
    var listed = remoteMutes.mutes(muter, 0, 20);
    assertThat(listed).hasSize(1);
    assertThat(listed.getFirst().account().following()).isTrue();
    assertThat(listed.getFirst().notifications()).isFalse();
    assertThat(listed.getFirst().expiresAt()).isAfter(Instant.now());

    remoteMutes.mute(muter, bob, true, null);
    update(
        "UPDATE federation_remote_mute SET expires_at = ?1 WHERE user_id = ?2 AND remote_actor_id = ?3",
        past(),
        muter,
        bob);
    assertThat(remoteMutes.mutes(muter, 0, 20))
        .extracting(row -> row.account().id())
        .containsExactly(alice);

    var unmuted = remoteMutes.unmute(muter, alice);

    assertThat(unmuted.muting()).isFalse();
    assertThat(
            count(
                "SELECT COUNT(*) FROM federation_remote_mute WHERE user_id = ?1 AND remote_actor_id = ?2",
                muter,
                alice))
        .isZero();
  }

  @Test
  void theExportCarriesBlockedAndMutedAccountsElsewhereAfterThoseHere() {
    Long carol = actor("carol");
    update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?1, ?2, NOW(6))",
        blocker,
        friend);
    update(
        "INSERT INTO user_mute (user_id, muted_user_id, hide_notifications, created_at)"
            + " VALUES (?1, ?2, FALSE, NOW(6))",
        blocker,
        stranger);
    block(blocker, alice);
    mute(blocker, bob, false, null);
    mute(blocker, carol, true, past());

    assertThat(exports.blocks(blocker))
        .containsExactly(
            new AccountExportReader.Account("f" + tag, null),
            new AccountExportReader.Account("alice" + tag, "alice-" + tag + ".example"));
    assertThat(exports.mutes(blocker))
        .containsExactly(
            new AccountExportReader.Mute(new AccountExportReader.Account("s" + tag, null), false),
            new AccountExportReader.Mute(
                new AccountExportReader.Account("bob" + tag, "bob-" + tag + ".example"), false));
  }

  @Test
  void blocksAndMutesGoWithTheMemberOrTheAccount() {
    block(blocker, alice);
    mute(blocker, bob, true, null);
    mute(muter, alice, true, null);

    update("DELETE FROM federation_remote_actor WHERE id = ?1", alice);
    assertThat(
            count("SELECT COUNT(*) FROM federation_remote_block WHERE remote_actor_id = ?1", alice))
        .isZero();
    assertThat(
            count("SELECT COUNT(*) FROM federation_remote_mute WHERE remote_actor_id = ?1", alice))
        .isZero();

    update("DELETE FROM users WHERE id = ?1", blocker);
    assertThat(count("SELECT COUNT(*) FROM federation_remote_mute WHERE user_id = ?1", blocker))
        .isZero();
  }
}
