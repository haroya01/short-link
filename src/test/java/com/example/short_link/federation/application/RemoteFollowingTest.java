package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.FederationFollowingEntity;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowingRepository;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

@ExtendWith(MockitoExtension.class)
class RemoteFollowingTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
  private static final String ALICE = "https://mastodon.example/users/alice";
  private static final String ME = "https://kurl.me/ap/actors/pid";
  private static final LocalActor LOCAL =
      new LocalActor(new FederationUser(7L, "yuki", null, null), "pid", "pem");

  @Mock private FederationFollowingRepository followings;
  @Mock private RemoteActorRepository remoteActors;
  @Mock private RemoteAccountFinder finder;
  @Mock private FederationActorService localActors;
  @Mock private FederationActorRepository actorRows;
  @Mock private DeliveryQueue deliveries;

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
                "https://files.mastodon.example/a.png"),
            NOW);
    ReflectionTestUtils.setField(alice, "id", 42L);
    lenient().when(remoteActors.findById(42L)).thenReturn(Optional.of(alice));
    lenient().when(followings.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private RemoteFollowing following() {
    return new RemoteFollowing(
        followings,
        remoteActors,
        finder,
        localActors,
        actorRows,
        deliveries,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        JSON,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static FederationFollowingEntity row(String followId, boolean accepted) {
    FederationFollowingEntity row = new FederationFollowingEntity(7L, 42L, followId);
    if (accepted) {
      row.accept(NOW);
    }
    return row;
  }

  private JsonNode delivered(String activityId) {
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(eq(7L), eq(activityId), body.capture(), eq(List.of(ALICE + "/inbox")));
    return JSON.readTree(body.getValue());
  }

  @Test
  void lookingUpAnUnknownHandleIs404AndAKnownOneCarriesTheRelationship() {
    when(finder.find(" ghost@mastodon.example ")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> following().lookup(7L, " ghost@mastodon.example "))
        .isInstanceOfSatisfying(
            FederationException.class,
            e -> assertThat(e.errorCode()).isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND));

    when(finder.find("alice@mastodon.example")).thenReturn(Optional.of(alice));
    when(followings.find(7L, 42L)).thenReturn(Optional.of(row(ME + "#follows/1", false)));
    RemoteAccountView view = following().lookup(7L, "alice@mastodon.example");

    assertThat(view)
        .isEqualTo(
            new RemoteAccountView(
                42L,
                "alice@mastodon.example",
                "alice",
                "mastodon.example",
                "Alice",
                "https://files.mastodon.example/a.png",
                "https://mastodon.example/@alice",
                false,
                true));
  }

  @Test
  void anAccountWithoutAProfilePageLinksItsActor() {
    RemoteActorEntity bare =
        new RemoteActorEntity(
            new RemoteActorDocument(
                ALICE,
                ALICE + "#k",
                "pem",
                ALICE + "/inbox",
                null,
                null,
                "mastodon.example",
                null,
                null,
                null),
            NOW);
    ReflectionTestUtils.setField(bare, "id", 43L);
    when(remoteActors.findById(43L)).thenReturn(Optional.of(bare));
    when(followings.find(7L, 43L)).thenReturn(Optional.of(row(ME + "#follows/1", true)));

    RemoteAccountView view = following().account(7L, 43L);

    assertThat(view.acct()).isEqualTo("@mastodon.example");
    assertThat(view.url()).isEqualTo(ALICE);
    assertThat(view.following()).isTrue();
    assertThat(view.requested()).isFalse();
  }

  @Test
  void anUnknownAccountIdIs404() {
    when(remoteActors.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> following().account(7L, 99L)).isInstanceOf(FederationException.class);
    assertThatThrownBy(() -> following().follow(7L, 99L)).isInstanceOf(FederationException.class);
  }

  @Test
  void followingSendsAFollowToTheirInboxAndWaitsForAccept() {
    when(localActors.byUserId(7L)).thenReturn(Optional.of(LOCAL));
    when(followings.find(7L, 42L)).thenReturn(Optional.empty());

    RemoteAccountView view = following().follow(7L, 42L);

    assertThat(view.requested()).isTrue();
    assertThat(view.following()).isFalse();
    ArgumentCaptor<FederationFollowingEntity> saved =
        ArgumentCaptor.forClass(FederationFollowingEntity.class);
    verify(followings).save(saved.capture());
    String followId = saved.getValue().getFollowActivityId();
    assertThat(followId).startsWith(ME + "#follows/");
    JsonNode follow = delivered(followId);
    assertThat(follow.path("@context").asString())
        .isEqualTo("https://www.w3.org/ns/activitystreams");
    assertThat(follow.path("type").asString()).isEqualTo("Follow");
    assertThat(follow.path("actor").asString()).isEqualTo(ME);
    assertThat(follow.path("object").asString()).isEqualTo(ALICE);
  }

  @Test
  void followingAgainSendsNothingNew() {
    when(localActors.byUserId(7L)).thenReturn(Optional.of(LOCAL));
    when(followings.find(7L, 42L)).thenReturn(Optional.of(row(ME + "#follows/1", true)));

    assertThat(following().follow(7L, 42L).following()).isTrue();
    verify(followings, never()).save(any());
    verifyNoInteractions(deliveries);
  }

  @Test
  void followingWithFederationOffIsRefused() {
    when(localActors.byUserId(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> following().follow(7L, 42L))
        .isInstanceOfSatisfying(
            FederationException.class,
            e -> assertThat(e.errorCode()).isEqualTo(FederationErrorCode.FEDERATION_DISABLED));
    verifyNoInteractions(deliveries);
  }

  @Test
  void unfollowingDropsTheRowAndUndoesTheFollowWeSent() {
    FederationFollowingEntity row = row(ME + "#follows/1", true);
    when(followings.find(7L, 42L)).thenReturn(Optional.of(row));
    when(actorRows.findByUserId(7L))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "pid", "PUB", "enc")));

    RemoteAccountView view = following().unfollow(7L, 42L);

    assertThat(view.following()).isFalse();
    assertThat(view.requested()).isFalse();
    verify(followings).delete(row);
    ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
    verify(deliveries).enqueue(eq(7L), id.capture(), anyString(), eq(List.of(ALICE + "/inbox")));
    assertThat(id.getValue()).startsWith(ME + "#undo/");
    JsonNode undo = delivered(id.getValue());
    assertThat(undo.path("type").asString()).isEqualTo("Undo");
    assertThat(undo.path("object").path("id").asString()).isEqualTo(ME + "#follows/1");
    assertThat(undo.path("object").path("object").asString()).isEqualTo(ALICE);
  }

  @Test
  void unfollowingSomeoneNotFollowedOrWithoutAnActorSendsNothing() {
    when(followings.find(7L, 42L))
        .thenReturn(Optional.empty(), Optional.of(row(ME + "#follows/1", false)));
    when(actorRows.findByUserId(7L)).thenReturn(Optional.empty());

    following().unfollow(7L, 42L);
    following().unfollow(7L, 42L);

    verify(followings).delete(any());
    verifyNoInteractions(deliveries);
  }

  @Test
  void anAcceptMatchesOurFollowByIdOrByWhoFollowedWhom() {
    FederationFollowingEntity byId = row(ME + "#follows/1", false);
    when(followings.findByFollowActivity(42L, ME + "#follows/1")).thenReturn(Optional.of(byId));

    assertThat(following().answered(alice, ME + "#follows/1", ME, true)).isTrue();
    assertThat(byId.accepted()).isTrue();
    assertThat(byId.getAcceptedAt()).isEqualTo(NOW);

    FederationFollowingEntity byPair = row(ME + "#follows/2", false);
    when(followings.findByFollowActivity(42L, "https://mastodon.example/their-id"))
        .thenReturn(Optional.empty());
    when(actorRows.findByPublicId("pid"))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "pid", "PUB", "enc")));
    when(followings.find(7L, 42L)).thenReturn(Optional.of(byPair));

    assertThat(following().answered(alice, "https://mastodon.example/their-id", ME, true)).isTrue();
    assertThat(byPair.accepted()).isTrue();
  }

  @Test
  void aRejectDropsTheRequestAndAnAnswerToNothingIsIgnored() {
    FederationFollowingEntity request = row(ME + "#follows/1", false);
    when(followings.findByFollowActivity(42L, ME + "#follows/1")).thenReturn(Optional.of(request));

    assertThat(following().answered(alice, ME + "#follows/1", null, false)).isTrue();
    verify(followings).delete(request);

    assertThat(following().answered(alice, null, null, true)).isFalse();
    assertThat(following().answered(alice, null, "https://elsewhere.example/u/x", true)).isFalse();
  }

  @Test
  void theFollowingListSkipsAccountsThatAreGone() {
    when(followings.page(7L, 20, 10))
        .thenReturn(
            List.of(row(ME + "#follows/1", true), new FederationFollowingEntity(7L, 5L, "x")));
    when(remoteActors.findAllById(List.of(42L, 5L))).thenReturn(List.of(alice));

    List<RemoteAccountView> page = following().following(7L, 2, 10);

    assertThat(page).extracting(RemoteAccountView::id).containsExactly(42L);
  }

  @Test
  void leavingUndoesEveryFollowThenForgetsThem() {
    when(followings.allForUser(7L))
        .thenReturn(
            List.of(row(ME + "#follows/1", true), new FederationFollowingEntity(7L, 5L, "x")));
    when(remoteActors.findAllById(List.of(42L, 5L))).thenReturn(List.of(alice));

    following().leave(7L, ME);

    verify(deliveries).enqueue(eq(7L), anyString(), anyString(), eq(List.of(ALICE + "/inbox")));
    verify(followings).deleteAllForUser(7L);
  }

  @Test
  void leavingWithNoFollowsDoesNothing() {
    when(followings.allForUser(7L)).thenReturn(List.of());

    following().leave(7L, ME);

    verify(followings, never()).deleteAllForUser(any());
    verifyNoInteractions(deliveries, remoteActors);
  }
}
