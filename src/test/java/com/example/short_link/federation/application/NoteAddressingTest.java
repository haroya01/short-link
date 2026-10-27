package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.NoteSnapshotReader.QuotedNote;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoteAddressingTest {

  private static final RemoteParents.Parent ALICE_POST =
      new RemoteParents.Parent(
          "https://mastodon.example/users/alice/statuses/1",
          "https://mastodon.example/users/alice",
          "@alice@mastodon.example",
          "https://mastodon.example/inbox",
          true);
  private static final RemoteParents.Parent CAROL_POST =
      new RemoteParents.Parent(
          "https://mastodon.example/users/carol/statuses/9",
          "https://mastodon.example/users/carol",
          "@carol@mastodon.example",
          "https://mastodon.example/inbox",
          true);

  @Mock private RemoteParents remoteParents;
  @Mock private RemoteAccountFinder finder;

  private final RemoteActorEntity bob = account("bob", "misskey.example");
  private final RemoteActorEntity eve = account("eve", "suspended.example");

  @BeforeEach
  void eveIsOnASuspendedServer() {
    ReflectionTestUtils.setField(eve, "serverBlock", "SUSPEND");
  }

  private static RemoteActorEntity account(String username, String domain) {
    String actorUri = "https://" + domain + "/users/" + username;
    return new RemoteActorEntity(
        new RemoteActorDocument(
            actorUri,
            actorUri + "#main-key",
            "pem",
            actorUri + "/inbox",
            "https://" + domain + "/inbox",
            username,
            domain,
            null,
            null,
            null,
            false,
            false),
        Instant.parse("2026-10-06T00:00:00Z"));
  }

  private static NoteSnapshot note(NoteVisibility visibility) {
    return new NoteSnapshot(
        52L,
        7L,
        "yuki",
        "@bob@misskey.example @eve@suspended.example 안녕",
        Instant.parse("2026-10-06T00:00:00Z"),
        null,
        900L,
        null,
        List.of(),
        new QuotedNote(
            77L,
            "carol",
            "https://mastodon.example/users/carol/statuses/9",
            "https://mastodon.example/@carol/9"),
        null,
        false,
        visibility,
        null,
        null);
  }

  @Test
  void aServedNoteReadsWhomItNamesFromWhatIsStoredHere() {
    when(remoteParents.of(900L)).thenReturn(Optional.of(ALICE_POST));
    when(remoteParents.of(77L)).thenReturn(Optional.of(CAROL_POST));
    when(finder.known("bob@misskey.example")).thenReturn(Optional.of(bob));
    when(finder.known("eve@suspended.example")).thenReturn(Optional.of(eve));

    assertThat(new NoteAddressing(remoteParents, finder).stored(note(NoteVisibility.PUBLIC)))
        .isEqualTo(new Addressing(ALICE_POST, List.of(bob), CAROL_POST));
    verify(finder, never()).find(anyString());
  }

  @Test
  void aSentNoteLooksUpWhomItNamesAndADirectOneQuotesNoOneElsewhere() {
    when(remoteParents.of(900L)).thenReturn(Optional.of(ALICE_POST));
    when(finder.find("bob@misskey.example")).thenReturn(Optional.of(bob));
    when(finder.find("eve@suspended.example")).thenReturn(Optional.of(eve));
    NoteAddressing addressing = new NoteAddressing(remoteParents, finder);

    assertThat(addressing.remoteNotes(note(NoteVisibility.DIRECT)))
        .isEqualTo(new Addressing(ALICE_POST, List.of(), null));
    assertThat(addressing.lookedUp(List.of("bob@misskey.example", "eve@suspended.example")))
        .containsExactly(bob);
    verify(finder, never()).known(anyString());
    verify(remoteParents, never()).of(77L);
  }
}
