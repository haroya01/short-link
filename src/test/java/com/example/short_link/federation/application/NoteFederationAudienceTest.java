package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.short_link.common.note.NoteSnapshotFixtures;
import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class NoteFederationAudienceTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String FOLLOWERS = "https://followers.example/inbox";
  private static final String REPOSTERS = "https://reposters.example/inbox";
  private static final String ALICE = "https://m.example/inbox";
  private static final String PUBLIC = "https://www.w3.org/ns/activitystreams#Public";

  record Sent(String type, String body, List<String> inboxes) {}

  @ParameterizedTest
  @EnumSource(NoteVisibility.class)
  void aNoteLeavesForTheServersOfThoseWhoMayReadIt(NoteVisibility visibility) {
    boolean shareable =
        visibility == NoteVisibility.PUBLIC || visibility == NoteVisibility.UNLISTED;
    boolean followers = shareable || visibility == NoteVisibility.PRIVATE;
    boolean leaves = visibility != NoteVisibility.ROOM;

    List<Sent> created = sent(visibility, federation -> federation.created(42L, 7L));
    List<Sent> edited = sent(visibility, federation -> federation.edited(42L, 7L));
    List<Sent> elsewhere = sent(visibility, federation -> federation.createdElsewhere(42L, 7L));
    List<Sent> reposted = sent(visibility, federation -> federation.reposted(900L, 42L, 8L));

    for (List<Sent> plain : List.of(created, edited)) {
      assertThat(plain).hasSize(followers ? 1 : 0);
      plain.forEach(sent -> assertThat(sent.inboxes()).containsExactly(FOLLOWERS));
    }
    assertThat(elsewhere).hasSize(leaves ? 1 : 0);
    elsewhere.forEach(
        sent ->
            assertThat(sent.inboxes())
                .containsExactlyElementsOf(followers ? List.of(FOLLOWERS, ALICE) : List.of(ALICE)));
    assertThat(reposted).hasSize(shareable ? 1 : 0);
    reposted.forEach(sent -> assertThat(sent.inboxes()).containsExactly(REPOSTERS));
    List<Sent> all = new ArrayList<>(created);
    all.addAll(edited);
    all.addAll(elsewhere);
    all.addAll(reposted);
    for (Sent sent : all) {
      assertThat(sent.body().contains(PUBLIC)).as(sent.type()).isEqualTo(shareable);
      assertThat(sent.body().contains("/followers\"")).as(sent.type()).isEqualTo(followers);
    }
  }

  private static List<Sent> sent(NoteVisibility visibility, Consumer<NoteFederation> act) {
    NoteSnapshotReader notes = mock(NoteSnapshotReader.class);
    RemoteParents parents = mock(RemoteParents.class);
    FederationActorService localActors = mock(FederationActorService.class);
    FederationSettings settings = mock(FederationSettings.class);
    FederationActorRepository actors = mock(FederationActorRepository.class);
    FederationFollowerRepository followers = mock(FederationFollowerRepository.class);
    DeliveryQueue deliveries = mock(DeliveryQueue.class);
    LocalActor writer = new LocalActor(new FederationUser(7L, "yuki", null, null), "pid", "PUB");
    lenient()
        .when(notes.find(42L))
        .thenReturn(
            Optional.of(
                NoteSnapshotFixtures.note(
                    42L,
                    7L,
                    "yuki",
                    "a note",
                    Instant.parse("2026-10-06T00:00:00Z"),
                    null,
                    41L,
                    null,
                    List.of(),
                    null,
                    null,
                    false,
                    visibility)));
    lenient()
        .when(parents.of(41L))
        .thenReturn(
            Optional.of(
                new RemoteParents.Parent(
                    "https://m.example/users/alice/statuses/9",
                    "https://m.example/users/alice",
                    "@alice@m.example",
                    ALICE,
                    true)));
    lenient().when(localActors.byUserId(7L)).thenReturn(Optional.of(writer));
    lenient().when(localActors.byUsername("yuki")).thenReturn(Optional.of(writer));
    lenient().when(settings.isEnabled(anyLong())).thenReturn(true);
    lenient()
        .when(actors.findByUserId(7L))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "pid", "PUB", "enc")));
    lenient()
        .when(actors.findByUserId(8L))
        .thenReturn(Optional.of(new FederationActorEntity(8L, "rp", "PUB", "enc")));
    lenient().when(followers.deliveryInboxes(7L)).thenReturn(List.of(FOLLOWERS));
    lenient().when(followers.deliveryInboxes(8L)).thenReturn(List.of(REPOSTERS));
    act.accept(
        new NoteFederation(
            notes,
            parents,
            mock(RemoteAccountFinder.class),
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
            new TransactionTemplate(mock(PlatformTransactionManager.class))));
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> inboxes = ArgumentCaptor.forClass(List.class);
    verify(deliveries, org.mockito.Mockito.atLeast(0))
        .enqueue(anyLong(), anyString(), body.capture(), inboxes.capture());
    List<Sent> sent = new ArrayList<>();
    for (int i = 0; i < body.getAllValues().size(); i++) {
      String json = body.getAllValues().get(i);
      sent.add(
          new Sent(
              JSON.readTree(json).path("type").asString(), json, inboxes.getAllValues().get(i)));
    }
    return sent;
  }
}
