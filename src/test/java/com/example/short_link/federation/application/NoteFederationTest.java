package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteSnapshotFixtures;
import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NoteFederationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final NoteSnapshot NOTE =
      NoteSnapshotFixtures.note(
          42L,
          7L,
          "yuki",
          "hi",
          Instant.parse("2026-10-06T00:00:00Z"),
          null,
          null,
          null,
          List.of());

  @Mock private NoteSnapshotReader notes;
  @Mock private RemoteParents remoteParents;
  @Mock private RemoteAccountFinder finder;
  @Mock private FederationActorService localActors;
  @Mock private FederationSettings settings;
  @Mock private FederationActorRepository actors;
  @Mock private FederationFollowerRepository followers;
  @Mock private DeliveryQueue deliveries;

  private NoteFederation service() {
    return new NoteFederation(
        notes,
        remoteParents,
        finder,
        localActors,
        settings,
        actors,
        followers,
        new NoteDocuments(
            new FederationUrls(
                new FederationProperties("https://kurl.me", "https://blog.kurl.me"))),
        deliveries,
        JSON,
        Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC),
        new TransactionTemplate(mock(PlatformTransactionManager.class)));
  }

  private void federating() {
    when(settings.isEnabled(7L)).thenReturn(true);
    when(actors.findByUserId(7L))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "pid", "PUB", "enc")));
    when(followers.deliveryInboxes(7L))
        .thenReturn(List.of("https://a.example/inbox", "https://b.example/inbox"));
  }

  @Test
  void aNewNoteIsQueuedOncePerFollowerServer() {
    when(notes.find(42L)).thenReturn(Optional.of(NOTE));
    federating();

    service().created(42L, 7L);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/notes/42/activity"),
            body.capture(),
            org.mockito.ArgumentMatchers.eq(
                List.of("https://a.example/inbox", "https://b.example/inbox")));
    assertThat(JSON.readTree(body.getValue()).path("type").asString()).isEqualTo("Create");
  }

  @Test
  void editsAndDeletesFollowTheSamePath() {
    when(notes.find(42L)).thenReturn(Optional.of(NOTE));
    federating();

    service().edited(42L, 7L);
    service().deleted(42L, 7L);

    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/notes/42#updates/0"),
            any(),
            any());
    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/notes/42#delete"),
            any(),
            any());
  }

  @Test
  void aRepostAnnouncesTheNoteToTheRepostersFollowersOnlyWhileItsAuthorFederates() {
    when(actors.findByUserId(8L))
        .thenReturn(Optional.of(new FederationActorEntity(8L, "rp", "PUB", "enc")));
    when(followers.deliveryInboxes(8L)).thenReturn(List.of("https://c.example/inbox"));
    when(settings.isEnabled(8L)).thenReturn(true);
    when(notes.find(42L)).thenReturn(Optional.of(NOTE));
    when(localActors.byUsername("yuki"))
        .thenReturn(
            Optional.of(new LocalActor(new FederationUser(7L, "yuki", null, null), "pid", "PUB")),
            Optional.empty());

    service().reposted(900L, 42L, 8L);
    service().unreposted(900L, 42L, 8L);
    service().reposted(901L, 42L, 8L);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(8L),
            org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/reposts/900"),
            body.capture(),
            org.mockito.ArgumentMatchers.eq(List.of("https://c.example/inbox")));
    assertThat(JSON.readTree(body.getValue()).path("type").asString()).isEqualTo("Announce");
    assertThat(JSON.readTree(body.getValue()).path("cc").get(1).asString())
        .isEqualTo("https://kurl.me/ap/actors/pid");
    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(8L),
            org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/reposts/900#undo"),
            any(),
            any());
    verify(deliveries, never())
        .enqueue(
            any(), org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/reposts/901"), any(), any());
  }

  @Test
  void nothingLeavesWithoutAnActorFollowersFederationOrTheNote() {
    when(actors.findByUserId(8L)).thenReturn(Optional.empty());
    service().created(1L, 8L);

    when(actors.findByUserId(9L))
        .thenReturn(Optional.of(new FederationActorEntity(9L, "p9", "PUB", "enc")));
    when(followers.deliveryInboxes(9L)).thenReturn(List.of());
    service().deleted(44L, 9L);

    federating();
    when(settings.isEnabled(7L)).thenReturn(false);
    service().deleted(42L, 7L);

    when(settings.isEnabled(7L)).thenReturn(true);
    when(notes.find(1L)).thenReturn(Optional.empty());
    service().created(1L, 7L);
    service().edited(1L, 7L);

    verifyNoInteractions(deliveries);
    verify(notes, never()).find(44L);
  }

  private static final RemoteParents.Parent ALICE_NOTE =
      new RemoteParents.Parent(
          "https://m.example/users/alice/statuses/9",
          "https://m.example/users/alice",
          "@alice@m.example",
          "https://m.example/inbox",
          true);
  private static final LocalActor WRITER =
      new LocalActor(new FederationUser(7L, "yuki", null, null), "pid", "PUB");

  private static NoteSnapshot reply(NoteVisibility visibility) {
    return NoteSnapshotFixtures.note(
        43L,
        7L,
        "yuki",
        "an answer",
        Instant.parse("2026-10-06T00:00:00Z"),
        null,
        41L,
        null,
        List.of(),
        null,
        null,
        false,
        visibility);
  }

  @Test
  void aReplyToANoteElsewhereReachesItsAuthorsServerEvenWithoutFollowers() {
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER));
    when(notes.find(43L)).thenReturn(Optional.of(reply(NoteVisibility.PUBLIC)));
    when(remoteParents.of(41L)).thenReturn(Optional.of(ALICE_NOTE));
    when(followers.deliveryInboxes(7L)).thenReturn(List.of());

    service().createdElsewhere(43L, 7L);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/notes/43/activity"),
            body.capture(),
            org.mockito.ArgumentMatchers.eq(List.of("https://m.example/inbox")));
    var create = JSON.readTree(body.getValue());
    assertThat(create.path("object").path("inReplyTo").asString()).isEqualTo(ALICE_NOTE.uri());
    assertThat(create.path("cc").toString()).contains("https://m.example/users/alice");
    assertThat(create.path("object").path("tag").get(0).path("name").asString())
        .isEqualTo("@alice@m.example");
  }

  @Test
  void aDirectReplyElsewhereGoesToThatAuthorAloneAndNothingLeavesWithoutAnActor() {
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER), Optional.empty());
    when(notes.find(43L)).thenReturn(Optional.of(reply(NoteVisibility.DIRECT)));
    when(remoteParents.of(41L)).thenReturn(Optional.of(ALICE_NOTE));

    service().createdElsewhere(43L, 7L);
    service().createdElsewhere(43L, 7L);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            any(),
            body.capture(),
            org.mockito.ArgumentMatchers.eq(List.of("https://m.example/inbox")));
    var create = JSON.readTree(body.getValue());
    assertThat(create.path("to").toString()).isEqualTo("[\"https://m.example/users/alice\"]");
    assertThat(create.path("cc").size()).isZero();
    verify(followers, never()).deliveryInboxes(any());
  }

  @Test
  void anEditedReplyElsewhereReachesThatServerAsWellAsTheFollowers() {
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER));
    when(notes.find(43L)).thenReturn(Optional.of(reply(NoteVisibility.PUBLIC)));
    when(remoteParents.of(41L)).thenReturn(Optional.of(ALICE_NOTE));
    when(followers.deliveryInboxes(7L))
        .thenReturn(List.of("https://a.example/inbox", "https://m.example/inbox"));

    service().edited(43L, 7L, true);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/notes/43#updates/0"),
            body.capture(),
            org.mockito.ArgumentMatchers.eq(
                List.of("https://a.example/inbox", "https://m.example/inbox")));
    var update = JSON.readTree(body.getValue());
    assertThat(update.path("type").asString()).isEqualTo("Update");
    assertThat(update.path("object").path("inReplyTo").asString()).isEqualTo(ALICE_NOTE.uri());
    assertThat(update.path("cc").toString()).contains("https://m.example/users/alice");
  }

  @Test
  void anEditThatOwesNoOneElsewhereGoesToTheFollowersAloneOrNowhere() {
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER));
    when(notes.find(43L)).thenReturn(Optional.of(reply(NoteVisibility.PUBLIC)));
    when(remoteParents.of(41L)).thenReturn(Optional.empty());
    when(followers.deliveryInboxes(7L)).thenReturn(List.of("https://a.example/inbox"), List.of());

    service().edited(43L, 7L, true);
    service().edited(43L, 7L, true);

    verify(deliveries, org.mockito.Mockito.times(1))
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            any(),
            any(),
            org.mockito.ArgumentMatchers.eq(List.of("https://a.example/inbox")));
  }

  private static NoteSnapshot quotingAlice() {
    return NoteSnapshotFixtures.note(
        44L,
        7L,
        "yuki",
        "look",
        Instant.parse("2026-10-06T00:00:00Z"),
        null,
        null,
        null,
        List.of(),
        new NoteSnapshotReader.QuotedNote(
            41L, null, ALICE_NOTE.uri(), "https://m.example/@alice/9"),
        null,
        false,
        NoteVisibility.PUBLIC);
  }

  @Test
  void aQuoteOfANoteElsewhereReachesItsServerCopyingItsAuthorWithoutNamingThem() {
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER));
    when(notes.find(44L)).thenReturn(Optional.of(quotingAlice()));
    when(remoteParents.of(41L)).thenReturn(Optional.of(ALICE_NOTE));
    when(followers.deliveryInboxes(7L)).thenReturn(List.of("https://a.example/inbox"));

    service().createdElsewhere(44L, 7L);
    service().edited(44L, 7L, true);
    service().deleted(44L, 7L, null, List.of(), 41L);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries, org.mockito.Mockito.times(3))
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            any(),
            body.capture(),
            org.mockito.ArgumentMatchers.eq(
                List.of("https://a.example/inbox", "https://m.example/inbox")));
    var create = JSON.readTree(body.getAllValues().get(0));
    assertThat(create.path("type").asString()).isEqualTo("Create");
    assertThat(create.path("object").path("quoteUri").asString()).isEqualTo(ALICE_NOTE.uri());
    assertThat(create.path("object").path("_misskey_quote").asString()).isEqualTo(ALICE_NOTE.uri());
    assertThat(create.path("object").path("content").asString())
        .contains("RE: <a href=\"https://m.example/@alice/9\">");
    assertThat(create.path("cc").toString()).contains("https://m.example/users/alice");
    assertThat(create.path("object").path("cc").toString())
        .contains("https://m.example/users/alice");
    assertThat(create.path("object").path("tag").size()).isZero();
    assertThat(create.path("object").path("inReplyTo").isNull()).isTrue();
    assertThat(JSON.readTree(body.getAllValues().get(1)).path("type").asString())
        .isEqualTo("Update");
    assertThat(JSON.readTree(body.getAllValues().get(2)).path("type").asString())
        .isEqualTo("Delete");
  }

  @Test
  void aDirectNoteQuotingANoteElsewhereNeverLeavesForThatServer() {
    NoteSnapshot directQuote =
        NoteSnapshotFixtures.note(
            44L,
            7L,
            "yuki",
            "look",
            Instant.parse("2026-10-06T00:00:00Z"),
            null,
            null,
            null,
            List.of(),
            new NoteSnapshotReader.QuotedNote(
                41L, null, ALICE_NOTE.uri(), "https://m.example/@alice/9"),
            null,
            false,
            NoteVisibility.DIRECT);
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER));
    when(notes.find(44L)).thenReturn(Optional.of(directQuote));
    lenient()
        .when(followers.deliveryInboxes(7L))
        .thenReturn(List.of("https://a.example/inbox", "https://b.example/inbox"));

    service().createdElsewhere(44L, 7L);
    service().edited(44L, 7L, true);

    verify(deliveries, never()).enqueue(any(), any(), any(), any());
    verify(remoteParents, never()).of(41L);
  }

  @Test
  void aDirectReplyElsewhereThatAlsoQuotesAnotherServerReachesOnlyThePersonItAnswers() {
    NoteSnapshot directReplyQuoting =
        NoteSnapshotFixtures.note(
            43L,
            7L,
            "yuki",
            "an answer",
            Instant.parse("2026-10-06T00:00:00Z"),
            null,
            41L,
            null,
            List.of(),
            new NoteSnapshotReader.QuotedNote(
                50L, null, "https://q.example/notes/50", "https://q.example/@carol/50"),
            null,
            false,
            NoteVisibility.DIRECT);
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER));
    when(notes.find(43L)).thenReturn(Optional.of(directReplyQuoting));
    when(remoteParents.of(41L)).thenReturn(Optional.of(ALICE_NOTE));

    service().createdElsewhere(43L, 7L);
    service().edited(43L, 7L, true);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries, org.mockito.Mockito.times(2))
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            any(),
            body.capture(),
            org.mockito.ArgumentMatchers.eq(List.of("https://m.example/inbox")));
    for (String sent : body.getAllValues()) {
      assertThat(JSON.readTree(sent).path("to").toString())
          .isEqualTo("[\"https://m.example/users/alice\"]");
      assertThat(sent).doesNotContain("https://q.example/users");
    }
    verify(remoteParents, never()).of(50L);
    verify(followers, never()).deliveryInboxes(any());
  }

  @Test
  void aQuoteOfAMembersNoteTakesTheUsualPathAndNamesNoServer() {
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER));
    NoteSnapshot quotingMio =
        NoteSnapshotFixtures.note(
            44L,
            7L,
            "yuki",
            "look",
            Instant.parse("2026-10-06T00:00:00Z"),
            null,
            null,
            null,
            List.of(),
            new NoteSnapshotReader.QuotedNote(9L, "mio"),
            null,
            false,
            NoteVisibility.PUBLIC);
    when(notes.find(44L)).thenReturn(Optional.of(quotingMio));
    when(followers.deliveryInboxes(7L)).thenReturn(List.of("https://a.example/inbox"));

    service().edited(44L, 7L, true);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            any(),
            body.capture(),
            org.mockito.ArgumentMatchers.eq(List.of("https://a.example/inbox")));
    verify(remoteParents, never()).of(9L);
    assertThat(JSON.readTree(body.getValue()).path("object").path("quoteUri").asString())
        .isEqualTo("https://kurl.me/ap/notes/9");
  }

  @Test
  void aDeletedReplyElsewhereTellsThatServerAndTheOnesItNamed() {
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER));
    when(remoteParents.of(41L)).thenReturn(Optional.of(ALICE_NOTE));
    when(finder.find("bob@b.example")).thenReturn(Optional.empty());
    when(followers.deliveryInboxes(7L)).thenReturn(List.of());

    service().deleted(43L, 7L, 41L, List.of("bob@b.example"), null);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/notes/43#delete"),
            body.capture(),
            org.mockito.ArgumentMatchers.eq(List.of("https://m.example/inbox")));
    assertThat(JSON.readTree(body.getValue()).path("type").asString()).isEqualTo("Delete");
  }

  @Test
  void aDeleteThatNeverLeftForElsewhereTakesTheUsualPath() {
    when(notes.find(42L)).thenReturn(Optional.of(NOTE));
    federating();

    service().deleted(42L, 7L, null, List.of(), null);
    service().edited(42L, 7L, false);

    verify(localActors, never()).byUserId(any());
    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/notes/42#delete"),
            any(),
            any());
  }

  @Test
  void aBoostOfANoteElsewhereAndItsUndoReachTheFollowersAndThatServer() {
    when(localActors.byUserId(8L))
        .thenReturn(
            Optional.of(new LocalActor(new FederationUser(8L, "rp", null, null), "rp", "PUB")));
    when(remoteParents.of(41L)).thenReturn(Optional.of(ALICE_NOTE));
    when(followers.deliveryInboxes(8L)).thenReturn(List.of("https://c.example/inbox"));

    service().repostedRemote(5L, 41L, 8L, true);
    service().repostedRemote(5L, 41L, 8L, false);

    ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
    verify(deliveries, org.mockito.Mockito.times(2))
        .enqueue(
            org.mockito.ArgumentMatchers.eq(8L),
            any(),
            bodies.capture(),
            org.mockito.ArgumentMatchers.eq(
                List.of("https://c.example/inbox", "https://m.example/inbox")));
    var announce = JSON.readTree(bodies.getAllValues().get(0));
    assertThat(announce.path("type").asString()).isEqualTo("Announce");
    assertThat(announce.path("object").asString()).isEqualTo(ALICE_NOTE.uri());
    var undo = JSON.readTree(bodies.getAllValues().get(1));
    assertThat(undo.path("type").asString()).isEqualTo("Undo");
    assertThat(undo.path("object").path("object").asString()).isEqualTo(ALICE_NOTE.uri());
  }

  @Test
  void aPrivateNoteElsewhereIsNeverBoosted() {
    when(localActors.byUserId(8L))
        .thenReturn(
            Optional.of(new LocalActor(new FederationUser(8L, "rp", null, null), "rp", "PUB")));
    when(remoteParents.of(41L))
        .thenReturn(
            Optional.of(
                new RemoteParents.Parent(
                    ALICE_NOTE.uri(),
                    ALICE_NOTE.actorUri(),
                    "@alice@m.example",
                    ALICE_NOTE.inbox(),
                    false)));

    service().repostedRemote(5L, 41L, 8L, true);

    verifyNoInteractions(deliveries);
  }

  @Test
  void aLikeAndItsUndoGoOnlyToTheAuthorsServerEachWithAnIdOfItsOwn() {
    when(localActors.byUserId(8L))
        .thenReturn(
            Optional.of(new LocalActor(new FederationUser(8L, "rp", null, null), "rp", "PUB")));
    when(remoteParents.of(41L)).thenReturn(Optional.of(ALICE_NOTE));

    service().likedRemote(41L, 8L, true);
    service().likedRemote(41L, 8L, false);

    ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
    verify(deliveries, org.mockito.Mockito.times(2))
        .enqueue(
            org.mockito.ArgumentMatchers.eq(8L),
            ids.capture(),
            bodies.capture(),
            org.mockito.ArgumentMatchers.eq(List.of("https://m.example/inbox")));
    assertThat(ids.getAllValues())
        .containsExactly(
            "https://kurl.me/ap/actors/rp#likes/41/5000",
            "https://kurl.me/ap/actors/rp#likes/41/5000/undo");
    var undo = JSON.readTree(bodies.getAllValues().get(1));
    assertThat(undo.path("object").path("type").asString()).isEqualTo("Like");
    assertThat(undo.path("object").path("object").asString()).isEqualTo(ALICE_NOTE.uri());
  }

  @Test
  void nothingIsSentForANoteThatIsNotFromElsewhere() {
    when(localActors.byUserId(8L))
        .thenReturn(
            Optional.of(new LocalActor(new FederationUser(8L, "rp", null, null), "rp", "PUB")));
    when(remoteParents.of(41L)).thenReturn(Optional.empty());
    when(localActors.byUserId(9L)).thenReturn(Optional.empty());

    service().likedRemote(41L, 8L, true);
    service().repostedRemote(5L, 41L, 8L, true);
    service().likedRemote(41L, 9L, true);
    service().repostedRemote(5L, 41L, 9L, true);

    verifyNoInteractions(deliveries);
  }

  private static com.example.short_link.federation.domain.RemoteActorEntity bob() {
    return new com.example.short_link.federation.domain.RemoteActorEntity(
        new com.example.short_link.federation.domain.RemoteActorDocument(
            "https://b.example/users/bob",
            "https://b.example/users/bob#main-key",
            "pem",
            "https://b.example/users/bob/inbox",
            "https://b.example/inbox",
            "bob",
            "b.example",
            "https://b.example/@bob",
            null,
            null,
            false,
            false),
        Instant.parse("2026-10-07T00:00:00Z"));
  }

  private static NoteSnapshot naming(NoteVisibility visibility) {
    return NoteSnapshotFixtures.note(
        44L,
        7L,
        "yuki",
        "hello @bob@b.example and @ghost@nowhere.example",
        Instant.parse("2026-10-06T00:00:00Z"),
        null,
        null,
        null,
        List.of(),
        null,
        null,
        false,
        visibility);
  }

  @Test
  void aNoteNamingSomeoneElsewhereMentionsThemAndReachesTheirServer() {
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER));
    when(notes.find(44L)).thenReturn(Optional.of(naming(NoteVisibility.PUBLIC)));
    when(finder.find("bob@b.example")).thenReturn(Optional.of(bob()));
    when(finder.find("ghost@nowhere.example")).thenReturn(Optional.empty());
    when(followers.deliveryInboxes(7L)).thenReturn(List.of("https://a.example/inbox"));

    service().createdElsewhere(44L, 7L);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/notes/44/activity"),
            body.capture(),
            org.mockito.ArgumentMatchers.eq(
                List.of("https://a.example/inbox", "https://b.example/inbox")));
    var create = JSON.readTree(body.getValue());
    assertThat(create.path("cc").toString()).contains("https://b.example/users/bob");
    var tag = create.path("object").path("tag").get(0);
    assertThat(tag.path("type").asString()).isEqualTo("Mention");
    assertThat(tag.path("name").asString()).isEqualTo("@bob@b.example");
    assertThat(create.path("object").path("content").asString())
        .contains(
            "<span class=\"h-card\"><a href=\"https://b.example/@bob\" class=\"u-url mention\">@<span>bob</span></a></span>")
        .contains("@ghost@nowhere.example");
  }

  @Test
  void aDirectNoteNamingSomeoneElsewhereGoesToThemAlone() {
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER));
    when(notes.find(44L)).thenReturn(Optional.of(naming(NoteVisibility.DIRECT)));
    when(finder.find("bob@b.example")).thenReturn(Optional.of(bob()));
    when(finder.find("ghost@nowhere.example")).thenReturn(Optional.empty());

    service().createdElsewhere(44L, 7L);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            any(),
            body.capture(),
            org.mockito.ArgumentMatchers.eq(List.of("https://b.example/inbox")));
    assertThat(JSON.readTree(body.getValue()).path("to").toString())
        .isEqualTo("[\"https://b.example/users/bob\"]");
    verify(followers, never()).deliveryInboxes(any());
  }

  @Test
  void aNoteWhoseHandlesResolveToNoOneTakesTheUsualPath() {
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER));
    when(notes.find(44L)).thenReturn(Optional.of(naming(NoteVisibility.PUBLIC)));
    when(finder.find(any())).thenReturn(Optional.empty());
    when(followers.deliveryInboxes(7L))
        .thenReturn(List.of("https://a.example/inbox", "https://b.example/inbox"));

    service().createdElsewhere(44L, 7L);

    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/notes/44/activity"),
            any(),
            org.mockito.ArgumentMatchers.eq(
                List.of("https://a.example/inbox", "https://b.example/inbox")));
  }

  @Test
  void someoneNamedOnASuspendedServerIsNotReached() {
    var suspended = bob();
    org.springframework.test.util.ReflectionTestUtils.setField(suspended, "serverBlock", "SUSPEND");
    when(localActors.byUserId(7L)).thenReturn(Optional.of(WRITER));
    when(notes.find(44L)).thenReturn(Optional.of(naming(NoteVisibility.PUBLIC)));
    when(finder.find("bob@b.example")).thenReturn(Optional.of(suspended));
    when(finder.find("ghost@nowhere.example")).thenReturn(Optional.empty());
    when(followers.deliveryInboxes(7L))
        .thenReturn(List.of("https://a.example/inbox", "https://b.example/inbox"));

    service().createdElsewhere(44L, 7L);

    verify(deliveries)
        .enqueue(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("https://kurl.me/ap/notes/44/activity"),
            any(),
            org.mockito.ArgumentMatchers.eq(
                List.of("https://a.example/inbox", "https://b.example/inbox")));
  }
}
