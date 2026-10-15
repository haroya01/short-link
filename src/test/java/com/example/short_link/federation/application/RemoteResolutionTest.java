package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RemoteResolutionTest {

  private static final String ALICE = "https://mastodon.example/users/alice";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final RemoteAccountView VIEW =
      new RemoteAccountView(
          42L,
          "alice@mastodon.example",
          "alice",
          "mastodon.example",
          "Alice",
          null,
          "https://mastodon.example/@alice",
          false,
          false,
          false,
          false,
          false);

  @Mock private RemoteAccountFinder finder;
  @Mock private RemoteDocuments documents;
  @Mock private RemoteActorResolver resolver;
  @Mock private RemoteNoteIntake notes;
  @Mock private RemoteFollowing following;
  @Mock private NoteViewReader noteViews;

  private RemoteResolution resolution() {
    return new RemoteResolution(
        finder,
        documents,
        resolver,
        notes,
        following,
        noteViews,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")));
  }

  private static RemoteActorEntity alice() {
    return new RemoteActorEntity(
        new RemoteActorDocument(
            ALICE,
            ALICE + "#main-key",
            "pem",
            ALICE + "/inbox",
            null,
            "alice",
            "mastodon.example",
            null,
            null,
            null,
            false,
            false),
        Instant.parse("2026-10-06T00:00:00Z"));
  }

  private static FederationErrorCode codeOf(Runnable resolve) {
    try {
      resolve.run();
    } catch (FederationException e) {
      return e.errorCode();
    }
    throw new AssertionError("expected a FederationException");
  }

  @Test
  void aHandleResolvesToTheAccountAsTheViewerSeesIt() {
    RemoteActorEntity alice = alice();
    when(finder.resolve("@alice@mastodon.example")).thenReturn(alice);
    when(following.view(7L, alice)).thenReturn(VIEW);

    assertThat(resolution().resolve(7L, "  @alice@mastodon.example "))
        .isEqualTo(new RemoteResolution.Resolved(VIEW, null));
    verifyNoInteractions(documents, noteViews);
  }

  @Test
  void nothingAtAllIsAMalformedHandle() {
    when(finder.resolve(""))
        .thenThrow(new FederationException(FederationErrorCode.REMOTE_ACCOUNT_INVALID));

    assertThat(codeOf(() -> resolution().resolve(7L, null)))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_INVALID);
  }

  @Test
  void anAddressWithoutAHostIsMalformedAndOneOnThisServerIsNotFound() {
    RemoteResolution resolution = resolution();
    assertThat(codeOf(() -> resolution.resolve(7L, "https:///users/alice")))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_INVALID);
    assertThat(codeOf(() -> resolution.resolve(7L, "https://exa mple.com/@alice")))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_INVALID);
    assertThat(codeOf(() -> resolution.resolve(7L, "https://kurl.me/ap/actors/pid1")))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND);
    assertThat(codeOf(() -> resolution.resolve(7L, "HTTPS://blog.kurl.me/@yuki")))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND);
    verifyNoInteractions(finder, documents);
  }

  @Test
  void aProfileAddressResolvesToTheAccountItsDocumentDescribes() {
    URI profile = URI.create("https://mastodon.example/@alice");
    JsonNode person = JSON.readTree("{\"type\":\"Person\",\"id\":\"" + ALICE + "\"}");
    RemoteActorEntity alice = alice();
    when(documents.fetch(profile, profile.toString())).thenReturn(person);
    when(resolver.stored(person, profile)).thenReturn(Optional.of(alice));
    when(following.view(7L, alice)).thenReturn(VIEW);

    assertThat(resolution().resolve(7L, profile.toString()))
        .isEqualTo(new RemoteResolution.Resolved(VIEW, null));
    verifyNoInteractions(finder, notes);
  }

  @Test
  void aDocumentThatIsNeitherAnAccountNorANoteIsNotFound() {
    URI page = URI.create("https://mastodon.example/about");
    JsonNode collection = JSON.readTree("{\"type\":\"OrderedCollection\"}");
    when(documents.fetch(page, page.toString())).thenReturn(collection);
    when(resolver.stored(collection, page)).thenReturn(Optional.empty());

    assertThat(codeOf(() -> resolution().resolve(7L, page.toString())))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND);
  }

  @Test
  void aNoteAddressResolvesToTheNoteAsTheNoteListsShowIt() {
    Object noteView = Map.of("id", 9L);
    for (String type : new String[] {"Note", "Article", "Question"}) {
      URI page = URI.create("https://mastodon.example/@alice/" + type);
      JsonNode note = JSON.readTree("{\"type\":\"" + type + "\"}");
      when(documents.fetch(page, page.toString())).thenReturn(note);
      when(notes.fetched(note, page)).thenReturn(Optional.of(9L));
      when(noteViews.view(9L, 7L)).thenReturn(Optional.of(noteView));

      assertThat(resolution().resolve(7L, page.toString()))
          .isEqualTo(new RemoteResolution.Resolved(null, noteView));
    }
    verifyNoInteractions(resolver, following);
  }

  @Test
  void aNoteThatIsNotKeptOrNotVisibleIsNotFound() {
    URI page = URI.create("https://mastodon.example/@alice/1");
    JsonNode note = JSON.readTree("{\"type\":\"Note\"}");
    when(documents.fetch(page, page.toString())).thenReturn(note);
    when(notes.fetched(note, page)).thenReturn(Optional.empty(), Optional.of(9L));
    when(noteViews.view(9L, 7L)).thenReturn(Optional.empty());

    RemoteResolution resolution = resolution();
    assertThat(codeOf(() -> resolution.resolve(7L, page.toString())))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND);
    assertThat(codeOf(() -> resolution.resolve(7L, page.toString())))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND);
  }
}
