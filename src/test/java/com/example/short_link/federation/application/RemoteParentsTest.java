package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RemoteParentsTest {

  private static final String ALICE = "https://m.example/users/alice";

  @Mock private RemoteNotes remoteNotes;
  @Mock private RemoteActorRepository remoteActors;

  private RemoteParents parents() {
    return new RemoteParents(remoteNotes, remoteActors);
  }

  private static RemoteActorEntity actor(String username, String sharedInbox) {
    return new RemoteActorEntity(
        new RemoteActorDocument(
            ALICE,
            ALICE + "#main-key",
            "pem",
            ALICE + "/inbox",
            sharedInbox,
            username,
            "m.example",
            null,
            null,
            null),
        Instant.parse("2026-10-07T00:00:00Z"));
  }

  @Test
  void aNoteFromElsewhereIsAddressedThroughItsAuthor() {
    when(remoteNotes.target(5L))
        .thenReturn(Optional.of(new RemoteNotes.Target(ALICE + "/statuses/1", 42L, true)));
    when(remoteActors.findById(42L))
        .thenReturn(Optional.of(actor("alice", "https://m.example/inbox")));

    assertThat(parents().of(5L))
        .contains(
            new RemoteParents.Parent(
                ALICE + "/statuses/1", ALICE, "@alice@m.example", "https://m.example/inbox", true));
  }

  @Test
  void anAccountWithoutSharedInboxOrUsernameStillResolves() {
    when(remoteNotes.target(5L))
        .thenReturn(Optional.of(new RemoteNotes.Target(ALICE + "/statuses/1", 42L, false)));
    when(remoteActors.findById(42L)).thenReturn(Optional.of(actor(null, null)));

    RemoteParents.Parent parent = parents().of(5L).orElseThrow();
    assertThat(parent.inbox()).isEqualTo(ALICE + "/inbox");
    assertThat(parent.handle()).isEqualTo("@@m.example");
    assertThat(parent.shareable()).isFalse();
  }

  @Test
  void aNoteFromASuspendedServerIsNeverAddressed() {
    RemoteActorEntity suspended = actor("alice", "https://m.example/inbox");
    ReflectionTestUtils.setField(suspended, "serverBlock", "SUSPEND");
    when(remoteNotes.target(5L))
        .thenReturn(Optional.of(new RemoteNotes.Target(ALICE + "/statuses/1", 42L, true)));
    when(remoteActors.findById(42L)).thenReturn(Optional.of(suspended));

    assertThat(parents().of(5L)).isEmpty();
  }

  @Test
  void aLimitedServerIsStillAddressed() {
    RemoteActorEntity limited = actor("alice", "https://m.example/inbox");
    ReflectionTestUtils.setField(limited, "serverBlock", "LIMIT");
    when(remoteNotes.target(5L))
        .thenReturn(Optional.of(new RemoteNotes.Target(ALICE + "/statuses/1", 42L, true)));
    when(remoteActors.findById(42L)).thenReturn(Optional.of(limited));

    assertThat(parents().of(5L)).isPresent();
  }

  @Test
  void membersNotesAndMissingIdsAreNotFromElsewhere() {
    when(remoteNotes.target(6L)).thenReturn(Optional.empty());

    assertThat(parents().of(6L)).isEmpty();
    assertThat(parents().of(null)).isEmpty();
    verifyNoInteractions(remoteActors);
  }
}
