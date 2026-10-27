package com.example.short_link.federation.application.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.note.RemoteNoteReactions.Kind;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.LocalActor;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.application.relay.RelaySubscriptions;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.repository.FederationFollowingRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class InboxClassifierTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final FederationUrls URLS =
      new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));
  private static final String ALICE = "https://mastodon.example/users/alice";
  private static final String MALLORY = "https://mastodon.example/users/mallory";
  private static final String OWNER_URI = "https://kurl.me/ap/actors/owner1";
  private static final String NOTE_URI = "https://kurl.me/ap/notes/1";
  private static final LocalActor OWNER =
      new LocalActor(new FederationUser(7L, "haroya", null, null), "owner1", "pem");

  @Mock private FederationActorService localActors;
  @Mock private NoteSnapshotReader notes;
  @Mock private RemoteNotes remoteNotes;
  @Mock private FederationFollowingRepository followingRows;
  @Mock private RelaySubscriptions relays;

  private InboxClassifier classifier() {
    return new InboxClassifier(
        URLS, localActors, notes, remoteNotes, followingRows, relays, new RemoteNoteParser(URLS));
  }

  private static InboxActivity activity(String type, String object) {
    String body =
        """
        {"id":"https://mastodon.example/a/1","type":"%s","actor":"%s","object":%s}"""
            .formatted(type, ALICE, object);
    return ((InboxStep.Next<InboxActivity>)
            InboxActivity.parse(JSON, body.getBytes(StandardCharsets.UTF_8)))
        .value();
  }

  private static String quoted(String value) {
    return "\"" + value + "\"";
  }

  private static InboxStep<InboxIntent> act(InboxIntent intent) {
    return InboxStep.next(intent);
  }

  private static InboxStep<InboxIntent> ignored(String reason) {
    return InboxStep.done(InboxOutcome.ignored(reason));
  }

  private static NoteSnapshot note(Long id, NoteVisibility visibility) {
    return new NoteSnapshot(
        id,
        7L,
        "haroya",
        "hello",
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

  static Stream<Arguments> shapesAlone() {
    String follow = "https://mastodon.example/f/1";
    String like = "https://mastodon.example/l/1";
    String ourFollow = OWNER_URI + "#follows/9";
    return Stream.of(
        Arguments.of(activity("Undo", quoted(follow)), act(new InboxIntent.UndoById(follow))),
        Arguments.of(activity("Undo", "null"), InboxStep.done(InboxOutcome.malformed("fields"))),
        Arguments.of(
            activity(
                "Undo",
                """
                {"id":"%s","type":"Like","actor":"%s","object":"%s"}"""
                    .formatted(like, ALICE, NOTE_URI)),
            act(new InboxIntent.Unreact(1L, Kind.LIKE, like))),
        Arguments.of(
            activity(
                "Undo",
                """
                {"id":"%s","type":"Announce","actor":"%s","object":"https://elsewhere.example/1"}"""
                    .formatted(like, ALICE)),
            act(new InboxIntent.Unreact(null, Kind.ANNOUNCE, like))),
        Arguments.of(
            activity(
                "Undo",
                """
                {"id":"%s","type":"Follow","actor":"%s","object":"%s"}"""
                    .formatted(follow, ALICE, OWNER_URI)),
            act(new InboxIntent.Unfollow("owner1", follow))),
        Arguments.of(
            activity(
                "Undo",
                """
                {"id":"%s","type":"Follow","actor":"%s","object":"%s"}"""
                    .formatted(follow, MALLORY, OWNER_URI)),
            ignored("actor-mismatch")),
        Arguments.of(
            activity(
                "Undo",
                """
                {"id":"%s","type":"Block","actor":"%s","object":"%s"}"""
                    .formatted(follow, ALICE, OWNER_URI)),
            ignored("unsupported")),
        Arguments.of(
            activity(
                "Undo",
                """
                {"type":"Like","actor":"%s","object":"%s"}"""
                    .formatted(ALICE, NOTE_URI)),
            InboxStep.done(InboxOutcome.malformed("fields"))),
        Arguments.of(
            activity(
                "Accept",
                """
                {"id":"%s","type":"Follow","actor":"%s","object":"%s"}"""
                    .formatted(ourFollow, OWNER_URI, ALICE)),
            act(new InboxIntent.Answer(ourFollow, OWNER_URI, true))),
        Arguments.of(
            activity(
                "Accept",
                """
                {"id":"%s","type":"Follow","actor":"%s","object":"%s"}"""
                    .formatted(URLS.relayFollow("t"), URLS.instance(), ALICE)),
            act(new InboxIntent.Answer(URLS.relayFollow("t"), URLS.instance(), true))),
        Arguments.of(
            activity("Reject", quoted(ourFollow)),
            act(new InboxIntent.Answer(ourFollow, null, false))),
        Arguments.of(
            activity("Reject", quoted(URLS.relayFollow("t"))),
            act(new InboxIntent.Answer(URLS.relayFollow("t"), null, false))),
        Arguments.of(
            activity(
                "Accept",
                """
                {"id":"%s","type":"Follow","actor":"%s","object":"%s"}"""
                    .formatted(follow, MALLORY, ALICE)),
            ignored("unsupported")),
        Arguments.of(
            activity(
                "Accept",
                """
                {"id":"%s","type":"Like","actor":"%s","object":"%s"}"""
                    .formatted(ourFollow, OWNER_URI, ALICE)),
            ignored("unsupported")),
        Arguments.of(activity("Accept", quoted(follow)), ignored("unsupported")),
        Arguments.of(activity("Delete", quoted(ALICE)), act(new InboxIntent.Forget())),
        Arguments.of(activity("EmojiReact", quoted(NOTE_URI)), ignored("unsupported")),
        Arguments.of(activity("Block", quoted(OWNER_URI)), ignored("unsupported")),
        Arguments.of(activity("Create", quoted(NOTE_URI)), ignored("unsupported")),
        Arguments.of(
            activity("Create", "{\"type\":\"Person\",\"id\":\"" + ALICE + "\"}"),
            ignored("unsupported")),
        Arguments.of(activity("Update", "{\"type\":\"Person\"}"), ignored("unsupported")),
        Arguments.of(
            activity(
                "Create",
                """
                {"id":"https://mastodon.example/s/1","type":"Note","attributedTo":"%s"}"""
                    .formatted(MALLORY)),
            ignored("unsupported")),
        Arguments.of(
            activity(
                "Update",
                """
                {"id":"https://mastodon.example/s/1","type":"Note","attributedTo":"%s"}"""
                    .formatted(MALLORY)),
            ignored("unknown-note")));
  }

  @ParameterizedTest
  @MethodSource("shapesAlone")
  void whatTheActivityAloneSettlesIsSettledWithoutALookup(
      InboxActivity activity, InboxStep<InboxIntent> expected) {
    assertThat(classifier().classify(activity, Optional.empty())).isEqualTo(expected);
    verifyNoInteractions(localActors, notes, remoteNotes, followingRows, relays);
  }

  @Test
  void aFollowNamesAnActorHereAndThePersonalInboxOwnerNeedsNoLookup() {
    InboxClassifier classifier = classifier();
    InboxActivity follow = activity("Follow", quoted(OWNER_URI));

    assertThat(classifier.classify(follow, Optional.of(OWNER)))
        .isEqualTo(act(new InboxIntent.Follow(OWNER)));
    verifyNoInteractions(localActors);

    when(localActors.byPublicId("owner1")).thenReturn(Optional.of(OWNER));
    assertThat(classifier.classify(follow, Optional.empty()))
        .isEqualTo(act(new InboxIntent.Follow(OWNER)));
    when(localActors.byPublicId("gone1")).thenReturn(Optional.empty());
    assertThat(
            classifier.classify(
                activity("Follow", quoted("https://kurl.me/ap/actors/gone1")), Optional.empty()))
        .isEqualTo(ignored("unknown-target"));
    assertThat(classifier.classify(activity("Follow", quoted(ALICE)), Optional.empty()))
        .isEqualTo(ignored("unknown-target"));
  }

  @Test
  void aReactionNeedsANoteThatFederatesAndAnAnnounceAShareableOne() {
    InboxClassifier classifier = classifier();
    NoteSnapshot open = note(1L, NoteVisibility.PUBLIC);
    NoteSnapshot followersOnly = note(2L, NoteVisibility.PRIVATE);
    when(notes.find(1L)).thenReturn(Optional.of(open));
    when(notes.find(2L)).thenReturn(Optional.of(followersOnly));
    when(localActors.byUsername("haroya")).thenReturn(Optional.of(OWNER));

    assertThat(classifier.classify(activity("Like", quoted(NOTE_URI)), Optional.empty()))
        .isEqualTo(act(new InboxIntent.React(open, Kind.LIKE)));
    assertThat(
            classifier.classify(
                activity("Like", quoted("https://kurl.me/ap/notes/2")), Optional.empty()))
        .isEqualTo(act(new InboxIntent.React(followersOnly, Kind.LIKE)));
    assertThat(
            classifier.classify(
                activity("Announce", quoted("https://kurl.me/ap/notes/2")), Optional.empty()))
        .isEqualTo(ignored("unknown-target"));
  }

  @Test
  void anAnnounceFromAnAcceptedRelayIsRelayedWithoutReadingTheNote() {
    when(relays.isAccepted(ALICE)).thenReturn(true);

    assertThat(
            classifier()
                .classify(
                    activity("Announce", quoted("https://elsewhere.example/s/1")),
                    Optional.empty()))
        .isEqualTo(act(new InboxIntent.Relayed("https://elsewhere.example/s/1")));
    verifyNoInteractions(notes);
  }

  @Test
  void aNamedNoteAnsweringOurPollIsAVote() {
    NoteSnapshot poll = note(5L, NoteVisibility.PUBLIC);
    when(notes.find(5L)).thenReturn(Optional.of(poll));
    InboxActivity answer =
        activity(
            "Create",
            """
            {"id":"https://mastodon.example/v/1","type":"Note","name":"국수",
             "inReplyTo":"https://kurl.me/ap/notes/5","attributedTo":"%s"}"""
                .formatted(ALICE));

    assertThat(classifier().classify(answer, Optional.of(OWNER)))
        .isEqualTo(act(new InboxIntent.Vote(poll, "국수")));
    verifyNoInteractions(localActors);
  }

  @Test
  void aNoteIsReceivedWhenItNamesUsAnswersUsOrComesFromSomeoneWeFollow() {
    InboxClassifier classifier = classifier();
    InboxActivity addressed =
        activity(
            "Create",
            """
            {"id":"https://mastodon.example/s/1","type":"Note","attributedTo":"%s",
             "to":["%s"]}"""
                .formatted(ALICE, OWNER_URI));
    assertThat(classifier.classify(addressed, Optional.empty()))
        .isEqualTo(act(new InboxIntent.Receive(addressed.object(), addressed.json(), null)));
    verifyNoInteractions(notes, remoteNotes, followingRows);

    InboxActivity unasked =
        activity(
            "Create",
            """
            {"id":"https://mastodon.example/s/2","type":"Note","attributedTo":"%s"}"""
                .formatted(ALICE));
    when(followingRows.anyAcceptedFollowOf(ALICE)).thenReturn(false, true);
    assertThat(classifier.classify(unasked, Optional.empty())).isEqualTo(ignored("unsolicited"));
    assertThat(classifier.classify(unasked, Optional.empty()))
        .isEqualTo(act(new InboxIntent.Receive(unasked.object(), unasked.json(), null)));
  }

  @Test
  void anUpdateRevisesOnlyANoteWeKeptFromTheSameAuthor() {
    InboxActivity update =
        activity(
            "Update",
            """
            {"id":"https://mastodon.example/s/1","type":"Note","attributedTo":"%s"}"""
                .formatted(ALICE));
    when(remoteNotes.kept("https://mastodon.example/s/1"))
        .thenReturn(Optional.of(9L), Optional.empty());

    assertThat(classifier().classify(update, Optional.empty()))
        .isEqualTo(act(new InboxIntent.Revise(update.object(), update.json(), 9L)));
    assertThat(classifier().classify(update, Optional.empty())).isEqualTo(ignored("unknown-note"));
  }

  @Test
  void aDeleteRetractsAKeptNoteAndBuriesAnUnknownOneFromTheActorsServer() {
    String kept = "https://mastodon.example/s/1";
    String unknown = "https://mastodon.example/s/2";
    String elsewhere = "https://elsewhere.example/s/3";
    String tooLong = "https://mastodon.example/" + "x".repeat(512);
    when(remoteNotes.exists(kept)).thenReturn(true);
    when(remoteNotes.exists(unknown)).thenReturn(false);
    when(remoteNotes.exists(elsewhere)).thenReturn(false);
    when(remoteNotes.exists(tooLong)).thenReturn(false);
    InboxClassifier classifier = classifier();

    assertThat(classifier.classify(activity("Delete", quoted(kept)), Optional.empty()))
        .isEqualTo(act(new InboxIntent.Retract(kept)));
    assertThat(classifier.classify(activity("Delete", quoted(unknown)), Optional.empty()))
        .isEqualTo(act(new InboxIntent.Bury(unknown)));
    assertThat(classifier.classify(activity("Delete", quoted(elsewhere)), Optional.empty()))
        .isEqualTo(ignored("unsupported"));
    assertThat(classifier.classify(activity("Delete", quoted(tooLong)), Optional.empty()))
        .isEqualTo(ignored("unsupported"));
  }

  @Test
  void onlyDeletesOfTheActorOrOfAnUnknownNoteSettleForACachedKey() {
    assertThat(new InboxIntent.Forget().cachedKeyOnly()).isTrue();
    assertThat(new InboxIntent.Bury("https://mastodon.example/s/2").cachedKeyOnly()).isTrue();
    assertThat(new InboxIntent.Retract("https://mastodon.example/s/1").cachedKeyOnly()).isFalse();
    assertThat(new InboxIntent.Follow(OWNER).cachedKeyOnly()).isFalse();
  }
}
