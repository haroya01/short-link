package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import com.example.short_link.testsupport.FederationFixtures;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class RemoteQuotesTest {

  private static final FederationUrls URLS =
      new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String QUOTING =
      "https://mastodon.example/users/ana/statuses/117491551754057458";
  private static final String QUOTED =
      "https://news.example/users/desk/statuses/117491245825695182";
  private static final String QUOTED_PAGE = "https://news.example/@desk/117491245825695182";
  private static final String DESK = "https://news.example/users/desk";
  private static final String AUTHORIZATION =
      "https://news.example/users/desk/quote_authorizations/117491551939300305";
  private static final String TEXT = "The answer to that headline is no.";

  private final RemoteNoteParser parser = new RemoteNoteParser(URLS);

  @Mock private RemoteNotes remoteNotes;
  @Mock private FederationActorRepository actorRows;
  @Mock private FederationFollowers followers;
  @Mock private NoteSnapshotReader notes;
  @Mock private RemoteActorResolver resolver;
  @Mock private EarlyDeletes earlyDeletes;
  @Mock private RemoteDocuments documents;

  @BeforeEach
  void nobodyIsAddressed() {
    lenient().when(actorRows.findByPublicIds(any())).thenReturn(List.of());
  }

  private RemoteNoteIntake intake() {
    return new RemoteNoteIntake(
        parser, remoteNotes, actorRows, followers, notes, resolver, earlyDeletes, URLS, documents);
  }

  private static RemoteActorEntity actor(String uri, String user, String domain, long id) {
    RemoteActorEntity actor =
        new RemoteActorEntity(
            new RemoteActorDocument(
                uri,
                uri + "#main-key",
                "pem",
                uri + "/inbox",
                null,
                user,
                domain,
                null,
                null,
                null,
                false,
                false),
            Instant.parse("2026-10-23T00:00:00Z"));
    ReflectionTestUtils.setField(actor, "id", id);
    return actor;
  }

  private static RemoteActorEntity ana() {
    return actor("https://mastodon.example/users/ana", "ana", "mastodon.example", 41L);
  }

  private static ObjectNode fixture(String name) {
    return (ObjectNode) FederationFixtures.json(name);
  }

  private RemoteNoteParser.Parsed parsed(JsonNode object) {
    return parser.parse(object, object);
  }

  private List<RemoteNotes.Received> received(int times) {
    ArgumentCaptor<RemoteNotes.Received> captor =
        ArgumentCaptor.forClass(RemoteNotes.Received.class);
    verify(remoteNotes, times(times)).receive(captor.capture());
    return captor.getAllValues();
  }

  private void theQuotedPostCanBeFetched(JsonNode quoted) {
    when(documents.fetch(URI.create(QUOTED), QUOTED)).thenReturn(quoted);
    when(resolver.byActorUri(DESK))
        .thenReturn(Optional.of(actor(DESK, "desk", "news.example", 52L)));
  }

  @Test
  void mastodonsQuoteNamesWhatItQuotesAndItsInlineParagraphLeavesTheText() {
    RemoteNoteParser.Parsed note = parsed(fixture("mastodon-quote-note.json"));

    assertThat(note.quote())
        .isEqualTo(new RemoteNoteParser.Quote(QUOTED, QUOTED_PAGE, AUTHORIZATION, false));
    assertThat(note.body()).isEqualTo(TEXT);
    assertThat(note.linkedBody()).isEqualTo(TEXT + "\n\n" + QUOTED_PAGE);
  }

  @Test
  void misskeysQuoteIsReadFromItsOwnFieldAndHasNoAuthorization() {
    RemoteNoteParser.Parsed note = parsed(fixture("misskey-quote-note.json"));

    assertThat(note.quote())
        .isEqualTo(
            new RemoteNoteParser.Quote(
                "https://misskey.example/notes/asomfy31zvbr0b6k",
                "https://misskey.example/notes/asomfy31zvbr0b6k",
                null,
                false));
    assertThat(note.body())
        .isEqualTo(
            "Finished this drawing with a gradient map.\nhttps://assets.example/detail?id=1");
  }

  @Test
  void theQuoteFieldsAreReadInOrderQuoteFirst() {
    ObjectNode object = fixture("misskey-quote-note.json");
    object.put("quoteUri", "https://misskey.example/notes/third");
    object.put("quote", "https://misskey.example/notes/first");

    assertThat(parsed(object).quote().uri()).isEqualTo("https://misskey.example/notes/first");
    object.remove("quote");
    assertThat(parsed(object).quote().uri())
        .isEqualTo("https://misskey.example/notes/asomfy31zvbr0b6k");
    object.remove("quoteUrl");
    assertThat(parsed(object).quote().uri()).isEqualTo("https://misskey.example/notes/third");
  }

  @Test
  void withoutTheInlineParagraphOnlyAFirstLineNamingTheQuotedPostGoes() {
    ObjectNode object = fixture("mastodon-quote-note.json");
    object.remove("contentMap");
    object.put("content", "<p>RE: " + QUOTED + "</p><p>" + TEXT + "</p>");
    assertThat(parsed(object).body()).isEqualTo(TEXT);

    object.put("content", "<p>RE: https://news.example/users/desk/statuses/1</p><p>x</p>");
    assertThat(parsed(object).body())
        .isEqualTo("RE: https://news.example/users/desk/statuses/1\n\nx");

    ObjectNode plain = fixture("mastodon-quote-note.json");
    for (String field : List.of("quote", "quoteUri", "_misskey_quote", "quoteAuthorization")) {
      plain.remove(field);
    }
    assertThat(parsed(plain).quote()).isNull();
    assertThat(parsed(plain).body()).startsWith("RE: " + QUOTED_PAGE);
  }

  @Test
  void anAuthorizedQuoteKeepsTheQuotedPostOnceAndLinksIt() {
    when(documents.fetch(URI.create(AUTHORIZATION), AUTHORIZATION))
        .thenReturn(fixture("mastodon-quote-authorization.json"));
    when(remoteNotes.kept(QUOTED)).thenReturn(Optional.empty());
    theQuotedPostCanBeFetched(fixture("mastodon-quoted-note.json"));
    when(remoteNotes.receive(any())).thenReturn(Optional.of(77L), Optional.of(78L));

    assertThat(intake().keep(ana(), parsed(fixture("mastodon-quote-note.json")), null))
        .contains(78L);

    List<RemoteNotes.Received> kept = received(2);
    assertThat(kept.get(0).uri()).isEqualTo(QUOTED);
    assertThat(kept.get(0).quotedNoteId()).isNull();
    assertThat(kept.get(1).uri()).isEqualTo(QUOTING);
    assertThat(kept.get(1).quotedNoteId()).isEqualTo(77L);
    assertThat(kept.get(1).body()).isEqualTo(TEXT);
  }

  @Test
  void aQuotedPostAlreadyKeptIsLinkedWithoutAskingForIt() {
    when(documents.fetch(URI.create(AUTHORIZATION), AUTHORIZATION))
        .thenReturn(fixture("mastodon-quote-authorization.json"));
    when(remoteNotes.kept(QUOTED)).thenReturn(Optional.of(70L));
    when(remoteNotes.receive(any())).thenReturn(Optional.of(78L));

    intake().keep(ana(), parsed(fixture("mastodon-quote-note.json")), null);

    assertThat(received(1).getFirst().quotedNoteId()).isEqualTo(70L);
    verify(documents, never()).fetch(eq(URI.create(QUOTED)), any());
  }

  @Test
  void anAuthorizationThatDoesNotAnswerForThisQuoteLeavesALinkAndFetchesNothingMore() {
    List<ObjectNode> wrong = new ArrayList<>();
    for (String[] change :
        new String[][] {
          {"type", "Accept"},
          {"interactingObject", "https://mastodon.example/users/ana/statuses/1"},
          {"interactionTarget", "https://news.example/users/desk/statuses/1"},
          {"attributedTo", "https://elsewhere.example/users/desk"},
          {"id", "https://news.example/users/desk/quote_authorizations/1"}
        }) {
      ObjectNode authorization = fixture("mastodon-quote-authorization.json");
      authorization.put(change[0], change[1]);
      wrong.add(authorization);
    }
    when(documents.fetch(URI.create(AUTHORIZATION), AUTHORIZATION))
        .thenReturn(wrong.get(0), wrong.get(1), wrong.get(2), wrong.get(3), wrong.get(4))
        .thenThrow(
            new FederationException(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND, AUTHORIZATION));
    when(remoteNotes.receive(any())).thenReturn(Optional.of(78L));

    RemoteNoteIntake intake = intake();
    for (int i = 0; i < 6; i++) {
      intake.keep(ana(), parsed(fixture("mastodon-quote-note.json")), null);
    }

    assertThat(received(6))
        .allSatisfy(
            note -> {
              assertThat(note.quotedNoteId()).isNull();
              assertThat(note.body()).isEqualTo(TEXT + "\n\n" + QUOTED_PAGE);
            });
    verify(documents, never()).fetch(eq(URI.create(QUOTED)), any());
    verifyNoInteractions(resolver);
  }

  @Test
  void aQuoteWithoutAnAuthorizationIsALinkWithoutAsking() {
    when(remoteNotes.receive(any())).thenReturn(Optional.of(80L));

    intake()
        .keep(
            actor("https://misskey.example/users/9abcdefghi", "m", "misskey.example", 60L),
            parsed(fixture("misskey-quote-note.json")),
            null);

    RemoteNotes.Received note = received(1).getFirst();
    assertThat(note.quotedNoteId()).isNull();
    assertThat(note.body())
        .isEqualTo(
            "Finished this drawing with a gradient map.\nhttps://assets.example/detail?id=1"
                + "\n\nhttps://misskey.example/notes/asomfy31zvbr0b6k");
    verifyNoInteractions(documents);
  }

  @Test
  void aQuotedPostThatIsGoneOrNotForEveryoneLeavesALink() {
    when(documents.fetch(URI.create(AUTHORIZATION), AUTHORIZATION))
        .thenReturn(fixture("mastodon-quote-authorization.json"));
    when(remoteNotes.kept(QUOTED)).thenReturn(Optional.empty());
    ObjectNode followersOnly = fixture("mastodon-quoted-note.json");
    followersOnly.putArray("to").add(DESK + "/followers");
    followersOnly.putArray("cc");
    when(documents.fetch(URI.create(QUOTED), QUOTED))
        .thenThrow(new FederationException(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND, QUOTED))
        .thenThrow(new FederationException(FederationErrorCode.REMOTE_SERVER_UNREACHABLE, QUOTED))
        .thenReturn(followersOnly);
    when(resolver.byActorUri(DESK))
        .thenReturn(Optional.of(actor(DESK, "desk", "news.example", 52L)));
    when(remoteNotes.receive(any())).thenReturn(Optional.of(78L));

    RemoteNoteIntake intake = intake();
    for (int i = 0; i < 3; i++) {
      intake.keep(ana(), parsed(fixture("mastodon-quote-note.json")), null);
    }

    assertThat(received(3))
        .allSatisfy(
            note -> {
              assertThat(note.uri()).isEqualTo(QUOTING);
              assertThat(note.quotedNoteId()).isNull();
              assertThat(note.body()).endsWith("\n\n" + QUOTED_PAGE);
            });
  }

  @Test
  void theQuotedPostsOwnQuoteIsNotFollowed() {
    ObjectNode quoted = fixture("mastodon-quoted-note.json");
    quoted.put("quote", "https://third.example/users/x/statuses/1");
    quoted.put("quoteAuthorization", "https://third.example/users/x/quote_authorizations/1");
    when(documents.fetch(URI.create(AUTHORIZATION), AUTHORIZATION))
        .thenReturn(fixture("mastodon-quote-authorization.json"));
    when(remoteNotes.kept(QUOTED)).thenReturn(Optional.empty());
    theQuotedPostCanBeFetched(quoted);
    when(remoteNotes.receive(any())).thenReturn(Optional.of(77L), Optional.of(78L));

    intake().keep(ana(), parsed(fixture("mastodon-quote-note.json")), null);

    List<RemoteNotes.Received> kept = received(2);
    assertThat(kept.get(0).quotedNoteId()).isNull();
    assertThat(kept.get(0).body()).endsWith("\n\nhttps://third.example/users/x/statuses/1");
    assertThat(kept.get(1).quotedNoteId()).isEqualTo(77L);
    verify(documents, times(2)).fetch(any(), any());
  }

  @Test
  void aQuoteOfAMembersPublicNoteIsLinkedAndOfAnyOtherIsALink() {
    ObjectNode object = fixture("mastodon-quote-note.json");
    for (String field : List.of("quote", "quoteUri", "_misskey_quote")) {
      object.put(field, "https://kurl.me/ap/notes/3");
    }
    object.remove("quoteAuthorization");
    when(notes.find(3L))
        .thenReturn(Optional.of(snapshot(NoteVisibility.PUBLIC)))
        .thenReturn(Optional.of(snapshot(NoteVisibility.PRIVATE)))
        .thenReturn(Optional.empty());
    when(remoteNotes.receive(any())).thenReturn(Optional.of(78L));

    RemoteNoteIntake intake = intake();
    for (int i = 0; i < 3; i++) {
      intake.keep(ana(), parsed(object), null);
    }

    List<RemoteNotes.Received> kept = received(3);
    assertThat(kept.get(0).quotedNoteId()).isEqualTo(3L);
    assertThat(kept.get(1).quotedNoteId()).isNull();
    assertThat(kept.get(2).quotedNoteId()).isNull();
    verifyNoInteractions(documents);
  }

  @Test
  void aQuoteAServersApiAlreadyAcceptedIsNotAskedAboutAgain() {
    when(remoteNotes.kept(QUOTED)).thenReturn(Optional.empty());
    theQuotedPostCanBeFetched(fixture("mastodon-quoted-note.json"));
    when(remoteNotes.receive(any())).thenReturn(Optional.of(77L), Optional.of(78L));

    RemoteNoteParser.Parsed note =
        intake().quoted(parsed(fixture("mastodon-quote-note.json")).acceptingQuote());

    assertThat(note.quotedNoteId()).isEqualTo(77L);
    assertThat(note.quote()).isNull();
    verify(documents, never()).fetch(eq(URI.create(AUTHORIZATION)), any());
  }

  @Test
  void aNoteFromTheAccountsHistoryKeepsItsQuoteAsALink() {
    when(remoteNotes.receive(any())).thenReturn(Optional.of(78L));

    assertThat(intake().fromHistory(ana(), fixture("mastodon-quote-note.json"))).isTrue();

    assertThat(received(1).getFirst().body()).isEqualTo(TEXT + "\n\n" + QUOTED_PAGE);
    verifyNoInteractions(documents);
  }

  private static NoteSnapshot snapshot(NoteVisibility visibility) {
    return new NoteSnapshot(
        3L,
        7L,
        "haroya",
        "hi",
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
}
