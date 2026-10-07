package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.FollowRequestSettledEvent;
import com.example.short_link.common.event.FollowRequestedEvent;
import com.example.short_link.common.event.RemoteFollowedEvent;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.FederationFollowerEntity;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.FollowOnDomain;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import com.example.short_link.federation.domain.repository.UserDomainBlockRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
class FederationFollowersTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final LocalActor TARGET =
      new LocalActor(new FederationUser(7L, "haroya", null, null), "owner1", "pem");
  private static final LocalActor LOCKED_TARGET =
      new LocalActor(
          new FederationUser(7L, "haroya", null, null, null, List.of(), true), "owner1", "pem");
  private static final String TARGET_URI = "https://kurl.me/ap/actors/owner1";

  @Mock private FederationFollowerRepository followers;
  @Mock private UserDomainBlockRepository domainBlocks;
  @Mock private FederationActorRepository actorRows;
  @Mock private RemoteActorRepository remoteActors;
  @Mock private DeliveryQueue deliveries;
  @Mock private ApplicationEventPublisher events;

  private FederationFollowers service() {
    return new FederationFollowers(
        followers,
        domainBlocks,
        actorRows,
        remoteActors,
        deliveries,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        JSON,
        events);
  }

  private static RemoteActorEntity alice() {
    RemoteActorEntity alice =
        new RemoteActorEntity(
            new RemoteActorDocument(
                "https://mastodon.example/users/alice",
                "https://mastodon.example/users/alice#main-key",
                "pem",
                "https://mastodon.example/users/alice/inbox",
                "https://mastodon.example/inbox",
                "alice",
                "mastodon.example",
                null,
                null,
                null),
            Instant.parse("2026-10-06T00:00:00Z"));
    ReflectionTestUtils.setField(alice, "id", 42L);
    return alice;
  }

  private JsonNode enqueuedAccept() {
    ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(
            eq(7L),
            id.capture(),
            body.capture(),
            eq(List.of("https://mastodon.example/users/alice/inbox")));
    JsonNode accept = JSON.readTree(body.getValue());
    assertThat(accept.path("id").asString()).isEqualTo(id.getValue());
    return accept;
  }

  @Test
  void aNewFollowerIsStoredAndAcceptedAtTheirOwnInbox() {
    when(followers.find(7L, 42L)).thenReturn(Optional.empty());

    service().follow(TARGET, alice(), "https://mastodon.example/f/1");

    ArgumentCaptor<FederationFollowerEntity> saved =
        ArgumentCaptor.forClass(FederationFollowerEntity.class);
    verify(followers).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(7L);
    assertThat(saved.getValue().getRemoteActorId()).isEqualTo(42L);
    assertThat(saved.getValue().getFollowActivityId()).isEqualTo("https://mastodon.example/f/1");
    verify(events).publishEvent(new RemoteFollowedEvent(7L, 42L));

    JsonNode accept = enqueuedAccept();
    assertThat(accept.path("@context").asString())
        .isEqualTo("https://www.w3.org/ns/activitystreams");
    assertThat(accept.path("type").asString()).isEqualTo("Accept");
    assertThat(accept.path("id").asString()).startsWith(TARGET_URI + "#accepts/follows/");
    assertThat(accept.path("actor").asString()).isEqualTo(TARGET_URI);
    assertThat(accept.path("object").path("id").asString())
        .isEqualTo("https://mastodon.example/f/1");
    assertThat(accept.path("object").path("type").asString()).isEqualTo("Follow");
    assertThat(accept.path("object").path("actor").asString())
        .isEqualTo("https://mastodon.example/users/alice");
    assertThat(accept.path("object").path("object").asString()).isEqualTo(TARGET_URI);
  }

  @Test
  void aRepeatedFollowKeepsOneRowAndIsAcceptedAgain() {
    FederationFollowerEntity existing =
        new FederationFollowerEntity(7L, 42L, "https://mastodon.example/f/1");
    when(followers.find(7L, 42L)).thenReturn(Optional.of(existing));

    service().follow(TARGET, alice(), "https://mastodon.example/f/2");

    verify(followers).save(existing);
    verifyNoInteractions(events);
    assertThat(existing.getFollowActivityId()).isEqualTo("https://mastodon.example/f/2");
    assertThat(enqueuedAccept().path("object").path("id").asString())
        .isEqualTo("https://mastodon.example/f/2");
  }

  @Test
  void eachAcceptGetsItsOwnIdSoARefollowIsNotDeduplicatedAway() {
    when(followers.find(7L, 42L)).thenReturn(Optional.empty());
    FederationFollowers service = service();

    service.follow(TARGET, alice(), "https://mastodon.example/f/1");
    service.follow(TARGET, alice(), "https://mastodon.example/f/1");

    ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
    verify(deliveries, times(2)).enqueue(eq(7L), ids.capture(), anyString(), any());
    assertThat(ids.getAllValues().get(0)).isNotEqualTo(ids.getAllValues().get(1));
  }

  @Test
  void unfollowAndForgetGoStraightToStorage() {
    RemoteActorEntity alice = alice();
    when(followers.delete(7L, 42L)).thenReturn(1);
    when(followers.deleteByFollowActivity(42L, "https://mastodon.example/f/1")).thenReturn(0);
    FederationFollowers service = service();

    assertThat(service.unfollow(7L, alice)).isEqualTo(1);
    assertThat(service.unfollow(alice, "https://mastodon.example/f/1")).isZero();
    service.forget(alice);

    verify(remoteActors).delete(alice);
  }

  @Test
  void aFollowFromABlockedServerIsRejectedAndNeverStored() {
    when(domainBlocks.blocks(7L, "mastodon.example")).thenReturn(true);

    service().follow(TARGET, alice(), "https://mastodon.example/f/1");

    verify(followers, never()).save(any());
    verifyNoInteractions(events);
    JsonNode reject = enqueuedAccept();
    assertThat(reject.path("type").asString()).isEqualTo("Reject");
    assertThat(reject.path("id").asString()).startsWith(TARGET_URI + "#rejects/follows/");
    assertThat(reject.path("object").path("id").asString())
        .isEqualTo("https://mastodon.example/f/1");
  }

  @Test
  void blockingAServerRejectsEachFollowerThereAndForgetsThem() {
    when(followers.onDomain(7L, "mastodon.example"))
        .thenReturn(
            List.of(
                new FollowOnDomain<>(
                    new FederationFollowerEntity(7L, 42L, "https://mastodon.example/f/1"),
                    alice())));
    when(actorRows.findByUserId(7L))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "owner1", "PUB", "enc")));

    service().rejectDomain(7L, "mastodon.example");

    verify(followers).delete(7L, 42L);
    JsonNode reject = enqueuedAccept();
    assertThat(reject.path("type").asString()).isEqualTo("Reject");
    assertThat(reject.path("actor").asString()).isEqualTo(TARGET_URI);
  }

  @Test
  void aFollowOfALockedAccountWaitsUnansweredAsARequest() {
    when(followers.find(7L, 42L)).thenReturn(Optional.empty());

    service().follow(LOCKED_TARGET, alice(), "https://mastodon.example/f/1");

    ArgumentCaptor<FederationFollowerEntity> saved =
        ArgumentCaptor.forClass(FederationFollowerEntity.class);
    verify(followers).save(saved.capture());
    assertThat(saved.getValue().isPending()).isTrue();
    verify(events).publishEvent(new FollowRequestedEvent(7L, null, 42L));
    verifyNoInteractions(deliveries);
  }

  @Test
  void askingAgainWhileWaitingStaysOneQuietRequest() {
    FederationFollowerEntity waiting =
        new FederationFollowerEntity(7L, 42L, "https://mastodon.example/f/1");
    when(followers.find(7L, 42L)).thenReturn(Optional.of(waiting));

    service().follow(LOCKED_TARGET, alice(), "https://mastodon.example/f/2");

    assertThat(waiting.isPending()).isTrue();
    assertThat(waiting.getFollowActivityId()).isEqualTo("https://mastodon.example/f/2");
    verifyNoInteractions(events, deliveries);
  }

  @Test
  void aFollowerAcceptedBeforeLockingIsAcceptedAgainOnARepeatedFollow() {
    FederationFollowerEntity accepted =
        new FederationFollowerEntity(7L, 42L, "https://mastodon.example/f/1");
    accepted.accept(Instant.parse("2026-10-01T00:00:00Z"));
    when(followers.find(7L, 42L)).thenReturn(Optional.of(accepted));

    service().follow(LOCKED_TARGET, alice(), "https://mastodon.example/f/2");

    assertThat(enqueuedAccept().path("type").asString()).isEqualTo("Accept");
  }

  @Test
  void approvingARequestAcceptsTheOriginalFollow() {
    FederationFollowerEntity waiting =
        new FederationFollowerEntity(7L, 42L, "https://mastodon.example/f/1");
    when(followers.find(7L, 42L)).thenReturn(Optional.of(waiting));
    when(actorRows.findByUserId(7L))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "owner1", "PUB", "enc")));
    when(remoteActors.findById(42L)).thenReturn(Optional.of(alice()));

    service().authorize(7L, 42L);

    assertThat(waiting.isPending()).isFalse();
    verify(followers).save(waiting);
    verify(events).publishEvent(new FollowRequestSettledEvent(7L, null, 42L));
    JsonNode accept = enqueuedAccept();
    assertThat(accept.path("type").asString()).isEqualTo("Accept");
    assertThat(accept.path("object").path("id").asString())
        .isEqualTo("https://mastodon.example/f/1");
  }

  @Test
  void turningARequestDownRejectsItAndForgetsIt() {
    when(followers.find(7L, 42L))
        .thenReturn(
            Optional.of(new FederationFollowerEntity(7L, 42L, "https://mastodon.example/f/1")));
    when(actorRows.findByUserId(7L))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "owner1", "PUB", "enc")));
    when(remoteActors.findById(42L)).thenReturn(Optional.of(alice()));

    service().reject(7L, 42L);

    verify(followers).delete(7L, 42L);
    verify(events).publishEvent(new FollowRequestSettledEvent(7L, null, 42L));
    assertThat(enqueuedAccept().path("type").asString()).isEqualTo("Reject");
  }

  @Test
  void aFollowerAlreadyInIsNoRequestToApproveOrTurnDown() {
    FederationFollowerEntity accepted =
        new FederationFollowerEntity(7L, 42L, "https://mastodon.example/f/1");
    accepted.accept(Instant.parse("2026-10-01T00:00:00Z"));
    when(followers.find(7L, 42L)).thenReturn(Optional.of(accepted));
    when(followers.find(7L, 43L)).thenReturn(Optional.empty());
    FederationFollowers service = service();

    assertThatThrownBy(() -> service.authorize(7L, 42L))
        .isInstanceOf(FederationException.class)
        .extracting(e -> ((FederationException) e).errorCode())
        .isEqualTo(FederationErrorCode.REMOTE_FOLLOW_REQUEST_NOT_FOUND);
    assertThatThrownBy(() -> service.reject(7L, 43L)).isInstanceOf(FederationException.class);
    verify(followers, never()).delete(any(), any());
    verifyNoInteractions(events, deliveries);
  }

  @Test
  void anAnswerWithNoActorLeftGoesNowhere() {
    when(followers.find(7L, 42L))
        .thenReturn(
            Optional.of(new FederationFollowerEntity(7L, 42L, "https://mastodon.example/f/1")));
    when(actorRows.findByUserId(7L)).thenReturn(Optional.empty());

    service().authorize(7L, 42L);

    verifyNoInteractions(deliveries);
  }

  @Test
  void theRequestListShowsWhoAskedAndWhen() {
    FederationFollowerEntity waiting =
        new FederationFollowerEntity(7L, 42L, "https://mastodon.example/f/1");
    ReflectionTestUtils.setField(waiting, "createdAt", Instant.parse("2026-10-07T00:00:00Z"));
    when(followers.pending(7L, 0, FederationFollowers.REQUESTS_PAGE_SIZE))
        .thenReturn(List.of(new FollowOnDomain<>(waiting, alice())));

    List<RemoteFollowRequestView> requests = service().pending(7L, -1);

    assertThat(requests)
        .singleElement()
        .satisfies(
            view -> {
              assertThat(view.id()).isEqualTo(42L);
              assertThat(view.acct()).isEqualTo("alice@mastodon.example");
              assertThat(view.url()).isEqualTo("https://mastodon.example/users/alice");
              assertThat(view.requestedAt()).isEqualTo(Instant.parse("2026-10-07T00:00:00Z"));
            });
  }

  @Test
  void unlockingAcceptsEveryoneWaiting() {
    FederationFollowerEntity waiting =
        new FederationFollowerEntity(7L, 42L, "https://mastodon.example/f/1");
    when(followers.pending(7L)).thenReturn(List.of(new FollowOnDomain<>(waiting, alice())));
    when(actorRows.findByUserId(7L))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "owner1", "PUB", "enc")));

    service().acceptAll(7L);

    assertThat(waiting.isPending()).isFalse();
    verify(followers).save(waiting);
    assertThat(enqueuedAccept().path("type").asString()).isEqualTo("Accept");
  }

  @Test
  void unlockingWithNobodyWaitingDoesNothing() {
    when(followers.pending(7L)).thenReturn(List.of());

    service().acceptAll(7L);

    verifyNoInteractions(actorRows, deliveries);
  }
}
