package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.inbox.EarlyDeletes;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.testsupport.FederationFixtures;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RemoteNoteIntakeTest {

  private static final String ALICE = "https://mastodon.example/users/alice";
  private static final String NOTE = ALICE + "/statuses/1";
  private static final URI PAGE = URI.create("https://mastodon.example/@alice/1");
  private static final FederationUrls URLS =
      new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Mock private RemoteNotes remoteNotes;
  @Mock private FederationActorRepository actorRows;
  @Mock private FederationFollowers followers;
  @Mock private NoteSnapshotReader notes;
  @Mock private RemoteActorResolver resolver;
  @Mock private EarlyDeletes earlyDeletes;

  private RemoteNoteIntake intake() {
    return new RemoteNoteIntake(
        new RemoteNoteParser(URLS),
        remoteNotes,
        actorRows,
        followers,
        notes,
        resolver,
        earlyDeletes,
        URLS);
  }

  private static RemoteActorEntity alice(String domain) {
    RemoteActorEntity actor =
        new RemoteActorEntity(
            new RemoteActorDocument(
                ALICE,
                ALICE + "#main-key",
                "pem",
                ALICE + "/inbox",
                null,
                "alice",
                domain,
                null,
                null,
                null,
                false,
                false),
            Instant.parse("2026-10-06T00:00:00Z"));
    ReflectionTestUtils.setField(actor, "id", 42L);
    return actor;
  }

  private static JsonNode note(String id, String attributedTo, String to, String inReplyTo) {
    return JSON.readTree(
        """
        {"id":%s,"type":"Note","attributedTo":%s,"to":["%s"],"inReplyTo":%s,
         "url":"https://mastodon.example/@alice/1","content":"<p>hello</p>",
         "published":"2026-10-06T00:00:00Z"}"""
            .formatted(
                id == null ? "null" : "\"" + id + "\"",
                attributedTo == null ? "null" : "\"" + attributedTo + "\"",
                to,
                inReplyTo == null ? "null" : "\"" + inReplyTo + "\""));
  }

  private static JsonNode publicNote() {
    return note(NOTE, ALICE, ActivityStreams.PUBLIC, null);
  }

  @Test
  void aPublicNoteFromItsOwnServerIsKeptAsTheInboxWouldKeepIt() {
    when(remoteNotes.kept(NOTE)).thenReturn(Optional.empty());
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(alice("mastodon.example")));
    when(actorRows.findByPublicIds(Set.of())).thenReturn(List.of());
    when(remoteNotes.receive(any())).thenReturn(Optional.of(9L));

    assertThat(intake().fetched(publicNote(), PAGE)).contains(9L);
    ArgumentCaptor<RemoteNotes.Received> received =
        ArgumentCaptor.forClass(RemoteNotes.Received.class);
    verify(remoteNotes).receive(received.capture());
    assertThat(received.getValue().remoteActorId()).isEqualTo(42L);
    assertThat(received.getValue().uri()).isEqualTo(NOTE);
    assertThat(received.getValue().url()).isEqualTo("https://mastodon.example/@alice/1");
    assertThat(received.getValue().body()).isEqualTo("hello");
    assertThat(received.getValue().visibility()).isEqualTo(NoteVisibility.PUBLIC);
    assertThat(received.getValue().inReplyToLocalId()).isNull();
  }

  @Test
  void aNoteAlreadyKeptIsReturnedWithoutAskingForItsAuthor() {
    when(remoteNotes.kept(NOTE)).thenReturn(Optional.of(5L));

    assertThat(intake().fetched(publicNote(), PAGE)).contains(5L);
    verifyNoInteractions(resolver);
    verify(remoteNotes, never()).receive(any());
  }

  @Test
  void aNoteInsertedBySomeoneElseAtTheSameMomentIsFoundByItsAddress() {
    when(remoteNotes.kept(NOTE)).thenReturn(Optional.empty(), Optional.of(6L));
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(alice("mastodon.example")));
    when(actorRows.findByPublicIds(Set.of())).thenReturn(List.of());
    when(remoteNotes.receive(any())).thenReturn(Optional.empty());

    assertThat(intake().fetched(publicNote(), PAGE)).contains(6L);
  }

  @Test
  void aNoteWhoseIdOrAuthorLivesOnAnotherServerIsNotKept() {
    RemoteNoteIntake intake = intake();
    assertThat(intake.fetched(note(null, ALICE, ActivityStreams.PUBLIC, null), PAGE)).isEmpty();
    assertThat(
            intake.fetched(
                note("https://mastodon.example/" + "x".repeat(520), ALICE, "x", null), PAGE))
        .isEmpty();
    assertThat(intake.fetched(note("not a url", ALICE, ActivityStreams.PUBLIC, null), PAGE))
        .isEmpty();
    assertThat(intake.fetched(publicNote(), URI.create("https://elsewhere.example/@alice/1")))
        .isEmpty();
    verifyNoInteractions(remoteNotes, resolver);

    when(remoteNotes.kept(NOTE)).thenReturn(Optional.empty());
    assertThat(intake.fetched(note(NOTE, null, ActivityStreams.PUBLIC, null), PAGE)).isEmpty();
    assertThat(
            intake.fetched(
                note(NOTE, "https://elsewhere.example/users/alice", ActivityStreams.PUBLIC, null),
                PAGE))
        .isEmpty();
    verifyNoInteractions(resolver);
  }

  @Test
  void anAuthorWhoCannotBeReadIsOnASuspendedServerOrElsewhereKeepsNothing() {
    RemoteActorEntity suspended = alice("mastodon.example");
    ReflectionTestUtils.setField(suspended, "serverBlock", "SUSPEND");
    when(remoteNotes.kept(NOTE)).thenReturn(Optional.empty());
    when(resolver.byActorUri(ALICE))
        .thenReturn(Optional.empty(), Optional.of(suspended), Optional.of(alice("other.example")));

    RemoteNoteIntake intake = intake();
    for (int i = 0; i < 3; i++) {
      assertThat(intake.fetched(publicNote(), PAGE)).isEmpty();
    }
    verify(remoteNotes, never()).receive(any());
  }

  @Test
  void aNoteDeletedBeforeItWasAskedForOrNotForEveryoneIsNotKept() {
    when(remoteNotes.kept(NOTE)).thenReturn(Optional.empty());
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(alice("mastodon.example")));
    when(earlyDeletes.remembered(42L, NOTE)).thenReturn(true, false, false);

    RemoteNoteIntake intake = intake();
    assertThat(intake.fetched(publicNote(), PAGE)).isEmpty();
    assertThat(intake.fetched(note(NOTE, ALICE, ALICE + "/followers", null), PAGE)).isEmpty();
    assertThat(intake.fetched(note(NOTE, ALICE, "https://kurl.me/ap/actors/pid1", null), PAGE))
        .isEmpty();
    verify(remoteNotes, never()).receive(any());
  }

  @Test
  void aVideoAttributedToItsAccountAndChannelIsKeptUnderTheAccount() {
    String account = "https://peertube.example/accounts/maker";
    URI watch = URI.create("https://peertube.example/w/kVBbnS8f4E5yDLs8JhzHmb");
    RemoteActorEntity maker = alice("peertube.example");
    when(remoteNotes.kept(any())).thenReturn(Optional.empty());
    when(resolver.byActorUri(account)).thenReturn(Optional.of(maker));
    when(actorRows.findByPublicIds(Set.of())).thenReturn(List.of());
    when(remoteNotes.receive(any())).thenReturn(Optional.of(11L));

    assertThat(intake().fetched(FederationFixtures.json("peertube-video.json"), watch))
        .contains(11L);
    ArgumentCaptor<RemoteNotes.Received> received =
        ArgumentCaptor.forClass(RemoteNotes.Received.class);
    verify(remoteNotes).receive(received.capture());
    assertThat(received.getValue().shape().kind()).isEqualTo(RemoteNotes.Kind.VIDEO);
    assertThat(received.getValue().notifies()).isTrue();
  }

  @Test
  void aNoteFromTheAccountsHistoryIsKeptQuietly() {
    when(actorRows.findByPublicIds(Set.of())).thenReturn(List.of());
    when(remoteNotes.receive(any())).thenReturn(Optional.of(9L), Optional.empty());

    RemoteNoteIntake intake = intake();
    assertThat(intake.fromHistory(alice("mastodon.example"), publicNote())).isTrue();
    assertThat(intake.fromHistory(alice("mastodon.example"), publicNote())).isFalse();
    ArgumentCaptor<RemoteNotes.Received> received =
        ArgumentCaptor.forClass(RemoteNotes.Received.class);
    verify(remoteNotes, times(2)).receive(received.capture());
    assertThat(received.getValue().notifies()).isFalse();
    assertThat(received.getValue().inReplyToLocalId()).isNull();
    verifyNoInteractions(resolver, notes);
  }

  @Test
  void aHistoryNoteThatIsNotTheAccountsOwnPublicTopLevelNoteIsSkipped() {
    RemoteActorEntity alice = alice("mastodon.example");
    RemoteNoteIntake intake = intake();
    String pub = ActivityStreams.PUBLIC;

    assertThat(intake.fromHistory(alice, note(null, ALICE, pub, null))).isFalse();
    assertThat(
            intake.fromHistory(
                alice, note("https://mastodon.example/" + "x".repeat(520), ALICE, pub, null)))
        .isFalse();
    assertThat(
            intake.fromHistory(
                alice,
                JSON.readTree(
                    "{\"id\":\""
                        + NOTE
                        + "\",\"type\":\"Event\",\"attributedTo\":\""
                        + ALICE
                        + "\"}")))
        .isFalse();
    assertThat(
            intake.fromHistory(
                alice, note("https://elsewhere.example/statuses/1", ALICE, pub, null)))
        .isFalse();
    assertThat(
            intake.fromHistory(alice, note(NOTE, "https://mastodon.example/users/bob", pub, null)))
        .isFalse();
    assertThat(intake.fromHistory(alice, note(NOTE, ALICE, pub, "https://other.example/s/1")))
        .isFalse();
    verifyNoInteractions(earlyDeletes, remoteNotes);

    when(earlyDeletes.remembered(42L, NOTE)).thenReturn(true, false);
    assertThat(intake.fromHistory(alice, publicNote())).isFalse();
    assertThat(intake.fromHistory(alice, note(NOTE, ALICE, ALICE + "/followers", null))).isFalse();
    verifyNoInteractions(remoteNotes);
  }

  @Test
  void aReplyToAPublicMembersNoteJoinsItsThread() {
    when(remoteNotes.kept(NOTE)).thenReturn(Optional.empty());
    when(resolver.byActorUri(ALICE)).thenReturn(Optional.of(alice("mastodon.example")));
    when(notes.find(3L))
        .thenReturn(
            Optional.of(
                new NoteSnapshot(
                    3L,
                    7L,
                    "haroya",
                    "hi",
                    Instant.parse("2026-10-06T00:00:00Z"),
                    null,
                    null,
                    null,
                    List.of())));
    when(actorRows.findByPublicIds(Set.of())).thenReturn(List.of());
    when(remoteNotes.receive(any())).thenReturn(Optional.of(9L));

    assertThat(
            intake()
                .fetched(
                    note(NOTE, ALICE, ActivityStreams.PUBLIC, "https://kurl.me/ap/notes/3"), PAGE))
        .contains(9L);
    ArgumentCaptor<RemoteNotes.Received> received =
        ArgumentCaptor.forClass(RemoteNotes.Received.class);
    verify(remoteNotes).receive(received.capture());
    assertThat(received.getValue().inReplyToLocalId()).isEqualTo(3L);
  }

  @Test
  void aDeliveredNoteHasItsFirstAddressReadForALinkCardAndADuplicateDoesNot() {
    JsonNode object =
        JSON.readTree(
            """
            {"id":"%s","type":"Note","attributedTo":"%s","to":["%s"],
             "content":"<p>new video https://youtu.be/abc</p>",
             "published":"2026-10-06T00:00:00Z"}"""
                .formatted(NOTE, ALICE, ActivityStreams.PUBLIC));
    when(actorRows.findByPublicIds(Set.of())).thenReturn(List.of());
    when(remoteNotes.receive(any())).thenReturn(Optional.of(9L), Optional.empty());
    RemoteNoteIntake intake = intake();
    RemoteNoteParser.Parsed note = new RemoteNoteParser(URLS).parse(object, object);

    assertThat(intake.keep(alice("mastodon.example"), note, null)).contains(9L);
    assertThat(intake.keep(alice("mastodon.example"), note, null)).isEmpty();

    verify(remoteNotes).preview(9L, "new video https://youtu.be/abc", false, null);
    verify(remoteNotes, times(1)).preview(any(), any(), anyBoolean(), any());
  }
}
