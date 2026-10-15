package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.RemoteAccountBlockedEvent;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.OnRemoteAccount;
import com.example.short_link.federation.domain.RemoteAccountStanding;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.RemoteBlockEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import com.example.short_link.federation.domain.repository.RemoteBlockRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RemoteBlocksTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
  private static final String ALICE = "https://mastodon.example/users/alice";
  private static final String ME = "https://kurl.me/ap/actors/pid";

  @Mock private RemoteBlockRepository blocks;
  @Mock private RemoteAccountStandingReader standings;
  @Mock private RemoteFollowing following;
  @Mock private FederationFollowers followers;
  @Mock private FederationActorRepository actorRows;
  @Mock private DeliveryQueue deliveries;
  @Mock private ApplicationEventPublisher events;

  private RemoteActorEntity alice;

  @BeforeEach
  void aliceIsKnown() {
    alice =
        new RemoteActorEntity(
            new RemoteActorDocument(
                ALICE,
                ALICE + "#main-key",
                "pem",
                ALICE + "/inbox",
                "https://mastodon.example/inbox",
                "alice",
                "mastodon.example",
                "https://mastodon.example/@alice",
                "Alice",
                null),
            NOW);
    ReflectionTestUtils.setField(alice, "id", 42L);
    lenient().when(following.remote(42L)).thenReturn(alice);
    lenient().when(following.known(42L)).thenReturn(alice);
    lenient()
        .when(following.standing(7L, alice))
        .thenReturn(new RemoteAccountStanding(false, false, true));
  }

  private RemoteBlocks service() {
    return new RemoteBlocks(
        blocks,
        standings,
        following,
        followers,
        actorRows,
        deliveries,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        JSON,
        events,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void federating() {
    when(actorRows.findByUserId(7L))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "pid", "PUB", "enc")));
  }

  private JsonNode delivered() {
    ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries).enqueue(eq(7L), id.capture(), body.capture(), eq(List.of(ALICE + "/inbox")));
    JsonNode activity = JSON.readTree(body.getValue());
    assertThat(activity.path("id").asString()).isEqualTo(id.getValue());
    assertThat(activity.path("@context").asString())
        .isEqualTo("https://www.w3.org/ns/activitystreams");
    return activity;
  }

  @Test
  void blockingEndsFollowsBothWaysTellsTheirServerAndClearsTheirNotices() {
    when(blocks.find(7L, 42L)).thenReturn(Optional.empty());
    federating();

    RemoteAccountView view = service().block(7L, 42L);

    verify(following).stopFollowing(7L, Optional.of(ME), alice);
    verify(followers).drop(7L, Optional.of(ME), alice);
    ArgumentCaptor<RemoteBlockEntity> saved = ArgumentCaptor.forClass(RemoteBlockEntity.class);
    verify(blocks).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(7L);
    assertThat(saved.getValue().getRemoteActorId()).isEqualTo(42L);
    assertThat(saved.getValue().getActivityId()).startsWith(ME + "#blocks/");
    JsonNode block = delivered();
    assertThat(block.path("type").asString()).isEqualTo("Block");
    assertThat(block.path("id").asString()).isEqualTo(saved.getValue().getActivityId());
    assertThat(block.path("actor").asString()).isEqualTo(ME);
    assertThat(block.path("object").asString()).isEqualTo(ALICE);
    verify(events).publishEvent(new RemoteAccountBlockedEvent(7L, 42L));
    assertThat(view.blocking()).isTrue();
    assertThat(view.muting()).isTrue();
    assertThat(view.following()).isFalse();
    assertThat(view.requested()).isFalse();
  }

  @Test
  void blockingAgainChangesNothing() {
    when(blocks.find(7L, 42L))
        .thenReturn(Optional.of(new RemoteBlockEntity(7L, 42L, ME + "#blocks/1")));

    assertThat(service().block(7L, 42L).blocking()).isTrue();

    verify(blocks, never()).save(any());
    verify(following, never()).stopFollowing(any(), any(), any());
    verifyNoInteractions(followers, actorRows, deliveries, events);
  }

  @Test
  void blockingWithFederationOffKeepsTheBlockHereAndSendsNothing() {
    when(blocks.find(7L, 42L)).thenReturn(Optional.empty());
    when(actorRows.findByUserId(7L)).thenReturn(Optional.empty());

    service().block(7L, 42L);

    verify(following).stopFollowing(7L, Optional.empty(), alice);
    verify(followers).drop(7L, Optional.empty(), alice);
    ArgumentCaptor<RemoteBlockEntity> saved = ArgumentCaptor.forClass(RemoteBlockEntity.class);
    verify(blocks).save(saved.capture());
    assertThat(saved.getValue().getActivityId()).isNull();
    verifyNoInteractions(deliveries);
    verify(events).publishEvent(new RemoteAccountBlockedEvent(7L, 42L));
  }

  @Test
  void unblockingUndoesTheBlockThatWasSent() {
    RemoteBlockEntity row = new RemoteBlockEntity(7L, 42L, ME + "#blocks/1");
    when(blocks.find(7L, 42L)).thenReturn(Optional.of(row));
    federating();
    RemoteAccountStanding unblocked = new RemoteAccountStanding(false, false, true);
    RemoteAccountView expected = RemoteAccountView.of(alice, Optional.empty(), unblocked);
    when(following.view(7L, alice, unblocked)).thenReturn(expected);

    assertThat(service().unblock(7L, 42L)).isSameAs(expected);

    verify(blocks).delete(row);
    JsonNode undo = delivered();
    assertThat(undo.path("type").asString()).isEqualTo("Undo");
    assertThat(undo.path("id").asString()).startsWith(ME + "#undo/");
    assertThat(undo.path("actor").asString()).isEqualTo(ME);
    assertThat(undo.path("object").path("id").asString()).isEqualTo(ME + "#blocks/1");
    assertThat(undo.path("object").path("type").asString()).isEqualTo("Block");
    assertThat(undo.path("object").path("object").asString()).isEqualTo(ALICE);
  }

  @Test
  void unblockingWhatWasNeverSentOrNeverBlockedSendsNothing() {
    RemoteBlockEntity quiet = new RemoteBlockEntity(7L, 42L, null);
    when(blocks.find(7L, 42L)).thenReturn(Optional.of(quiet), Optional.empty());

    service().unblock(7L, 42L);
    service().unblock(7L, 42L);

    verify(blocks).delete(quiet);
    verifyNoInteractions(actorRows, deliveries);
  }

  @Test
  void unblockingWithFederationOffSinceSendsNothing() {
    when(blocks.find(7L, 42L))
        .thenReturn(Optional.of(new RemoteBlockEntity(7L, 42L, ME + "#blocks/1")));
    when(actorRows.findByUserId(7L)).thenReturn(Optional.empty());

    service().unblock(7L, 42L);

    verify(deliveries, never()).enqueue(any(), anyString(), anyString(), any());
  }

  @Test
  void theBlockListReadsEveryAccountsStandingInOneLookup() {
    RemoteActorEntity bob =
        new RemoteActorEntity(
            new RemoteActorDocument(
                "https://social.example/users/bob",
                "k",
                "pem",
                "https://social.example/users/bob/inbox",
                null,
                "bob",
                "social.example",
                null,
                null,
                null),
            NOW);
    ReflectionTestUtils.setField(bob, "id", 43L);
    when(blocks.page(7L, 20, 10))
        .thenReturn(
            List.of(
                new OnRemoteAccount<>(new RemoteBlockEntity(7L, 42L, null), alice),
                new OnRemoteAccount<>(new RemoteBlockEntity(7L, 43L, null), bob)));
    when(standings.of(7L, List.of(42L, 43L), NOW))
        .thenReturn(Map.of(42L, new RemoteAccountStanding(true, true, true)));

    List<RemoteAccountView> page = service().blocks(7L, 2, 10);

    assertThat(page).extracting(RemoteAccountView::id).containsExactly(42L, 43L);
    assertThat(page).extracting(RemoteAccountView::blocking).containsExactly(true, true);
    assertThat(page).extracting(RemoteAccountView::muting).containsExactly(true, false);
    assertThat(page).extracting(RemoteAccountView::domainBlocked).containsExactly(true, false);
  }
}
