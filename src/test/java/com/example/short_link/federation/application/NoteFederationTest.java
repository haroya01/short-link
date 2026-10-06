package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NoteFederationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final NoteSnapshot NOTE =
      new NoteSnapshot(
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
  @Mock private FederationActorService localActors;
  @Mock private FederationSettings settings;
  @Mock private FederationActorRepository actors;
  @Mock private FederationFollowerRepository followers;
  @Mock private DeliveryQueue deliveries;

  private NoteFederation service() {
    return new NoteFederation(
        notes,
        localActors,
        settings,
        actors,
        followers,
        new NoteDocuments(
            new FederationUrls(
                new FederationProperties("https://kurl.me", "https://blog.kurl.me"))),
        deliveries,
        JSON);
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
}
