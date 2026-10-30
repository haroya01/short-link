package com.example.short_link.federation.application.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteActorResolver;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationRelayEntity;
import com.example.short_link.federation.domain.FederationRelaySettingEntity;
import com.example.short_link.federation.domain.RelayMode;
import com.example.short_link.federation.domain.RelayState;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationRelayRepository;
import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import com.example.short_link.support.FakeTransactions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RelaySubscriptionsTest {

  private static final String RELAY = "https://relay.example/tag/dev";
  private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
  private static final FederationUrls URLS =
      new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Mock private FederationRelayRepository relays;
  @Mock private FederationRelayedNoteRepository relayed;
  @Mock private RemoteActorResolver resolver;
  @Mock private DeliveryQueue deliveries;

  private RelaySubscriptions subscriptions(boolean enabled) {
    return subscriptions(enabled, null);
  }

  private RelaySubscriptions subscriptions(boolean enabled, RelayMode startIn) {
    return new RelaySubscriptions(
        relays,
        relayed,
        resolver,
        deliveries,
        URLS,
        JSON,
        new RelayProperties(
            enabled,
            30,
            2_000,
            500,
            Duration.ofSeconds(5),
            Duration.ofSeconds(4),
            startIn,
            List.of()),
        Clock.fixed(NOW, ZoneOffset.UTC),
        FakeTransactions.template());
  }

  private static FederationRelaySettingEntity settingRow(RelayMode mode, Instant chosenAt) {
    FederationRelaySettingEntity row =
        BeanUtils.instantiateClass(FederationRelaySettingEntity.class);
    ReflectionTestUtils.setField(row, "mode", mode);
    ReflectionTestUtils.setField(row, "languages", "ko,ja");
    ReflectionTestUtils.setField(row, "chosenAt", chosenAt);
    return row;
  }

  private static RemoteActorEntity relayActor() {
    return new RemoteActorEntity(
        new RemoteActorDocument(
            RELAY,
            RELAY + "#key",
            "pem",
            RELAY,
            "https://relay.example/instance/relay.example",
            null,
            "relay.example",
            null,
            "#dev",
            null,
            false,
            false),
        NOW);
  }

  private static FederationRelayEntity row(Long id, RelayState state) {
    FederationRelayEntity row =
        new FederationRelayEntity(RELAY, RELAY, "#dev", URLS.relayFollow("f" + id));
    ReflectionTestUtils.setField(row, "id", id);
    if (state != RelayState.PENDING) {
      row.answered(state == RelayState.ACCEPTED, NOW);
    }
    return row;
  }

  private static FederationErrorCode codeOf(Runnable call) {
    try {
      call.run();
    } catch (FederationException e) {
      return e.errorCode();
    }
    throw new AssertionError("expected a FederationException");
  }

  @Test
  void subscribingFollowsTheRelayAsTheInstanceWithThePublicCollection() {
    when(resolver.byActorUri(RELAY)).thenReturn(Optional.of(relayActor()));
    when(relays.findByActorUri(RELAY)).thenReturn(Optional.empty());
    when(relays.save(any(FederationRelayEntity.class))).thenAnswer(call -> call.getArgument(0));

    RelaySubscriptions.View view = subscriptions(true).subscribe(" " + RELAY + " ");

    assertThat(view.state()).isEqualTo(RelayState.PENDING);
    assertThat(view.label()).isEqualTo("#dev");
    ArgumentCaptor<String> followId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries).enqueue(eq(null), followId.capture(), body.capture(), eq(List.of(RELAY)));
    assertThat(URLS.isRelayFollow(followId.getValue())).isTrue();
    JsonNode follow = JSON.readTree(body.getValue());
    assertThat(follow.path("id").asString()).isEqualTo(followId.getValue());
    assertThat(follow.path("type").asString()).isEqualTo("Follow");
    assertThat(follow.path("actor").asString()).isEqualTo("https://kurl.me/ap/instance");
    assertThat(follow.path("object").asString())
        .isEqualTo("https://www.w3.org/ns/activitystreams#Public");
    assertThat(subscriptions(true).isAccepted(RELAY)).isFalse();
  }

  @Test
  void aRelayAlreadySubscribedIsNotFollowedAgain() {
    when(resolver.byActorUri(RELAY)).thenReturn(Optional.of(relayActor()));
    when(relays.findByActorUri(RELAY)).thenReturn(Optional.of(row(3L, RelayState.ACCEPTED)));

    assertThat(subscriptions(true).subscribe(RELAY).id()).isEqualTo(3L);
    verifyNoInteractions(deliveries);
  }

  @Test
  void subscribingIsRefusedWhileRelaysAreOffOrForAnAddressThatIsNoRelay() {
    assertThat(codeOf(() -> subscriptions(false).subscribe(RELAY)))
        .isEqualTo(FederationErrorCode.RELAY_DISABLED);
    for (String bad :
        new String[] {"http://relay.example/tag/dev", "https://kurl.me/ap/instance", "a b", ""}) {
      assertThat(codeOf(() -> subscriptions(true).subscribe(bad)))
          .isEqualTo(FederationErrorCode.RELAY_INVALID);
    }
    when(resolver.byActorUri(RELAY)).thenReturn(Optional.empty());
    assertThat(codeOf(() -> subscriptions(true).subscribe(RELAY)))
        .isEqualTo(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND);
    verifyNoInteractions(deliveries);
    verify(relays, never()).save(any(FederationRelayEntity.class));
  }

  @Test
  void onlyTheRelaysOwnAcceptOfOurFollowLetsItsAnnouncesIn() {
    FederationRelayEntity pending = row(1L, RelayState.PENDING);
    List<FederationRelayEntity> rows = new ArrayList<>(List.of(pending));
    when(relays.findByFollowActivityId(pending.getFollowActivityId()))
        .thenReturn(Optional.of(pending));
    when(relays.list()).thenAnswer(call -> rows);
    RelaySubscriptions subscriptions = subscriptions(true);

    RemoteActorEntity someoneElse =
        new RemoteActorEntity(
            new RemoteActorDocument(
                "https://evil.example/actor",
                "https://evil.example/actor#key",
                "pem",
                "https://evil.example/inbox",
                null,
                null,
                "evil.example",
                null,
                null,
                null,
                false,
                false),
            NOW);
    assertThat(subscriptions.answered(someoneElse, pending.getFollowActivityId(), true)).isFalse();
    assertThat(subscriptions.isAccepted(RELAY)).isFalse();

    assertThat(subscriptions.answered(relayActor(), pending.getFollowActivityId(), true)).isTrue();
    assertThat(pending.getState()).isEqualTo(RelayState.ACCEPTED);
    assertThat(pending.getAcceptedAt()).isEqualTo(NOW);
    assertThat(subscriptions.isAccepted(RELAY)).isTrue();
    assertThat(subscriptions.relayId(RELAY)).isEqualTo(1L);

    assertThat(subscriptions.answered(relayActor(), pending.getFollowActivityId(), false)).isTrue();
    assertThat(pending.getState()).isEqualTo(RelayState.REJECTED);
    assertThat(subscriptions.isAccepted(RELAY)).isFalse();
    assertThat(subscriptions.relayId(RELAY)).isNull();
  }

  @Test
  void theListingCountsTheNotesEachRelayBroughtThatAreStillKept() {
    when(relays.list())
        .thenReturn(List.of(row(1L, RelayState.ACCEPTED), row(2L, RelayState.PENDING)));
    when(relayed.countByRelay()).thenReturn(Map.of(1L, 5L, 0L, 3L));

    RelaySubscriptions.Listing listing = subscriptions(true).list();

    assertThat(listing.relays()).extracting(RelaySubscriptions.View::kept).containsExactly(5L, 0L);
    assertThat(listing.kept()).isEqualTo(8L);
  }

  @Test
  void theModeTheServerIsStartedInHoldsOnlyUntilAnAdminOrTheBreakerHasChosenOne() {
    FederationRelaySettingEntity fresh = settingRow(RelayMode.COUNT, null);
    when(relays.setting()).thenReturn(fresh);
    RelaySubscriptions started = subscriptions(true, RelayMode.STORE);
    started.load();
    assertThat(started.setting())
        .isEqualTo(new RelaySubscriptions.Setting(RelayMode.STORE, List.of("ko", "ja")));
    assertThat(fresh.getMode()).isEqualTo(RelayMode.STORE);
    assertThat(fresh.getChosenAt()).isNull();
    verify(relays).save(fresh);

    RelaySubscriptions restarted = subscriptions(true, RelayMode.COUNT);
    restarted.load();
    assertThat(restarted.setting().mode()).isEqualTo(RelayMode.COUNT);

    restarted.change(RelayMode.OFF, List.of("ko"));
    assertThat(fresh.getChosenAt()).isEqualTo(NOW);
    RelaySubscriptions afterChoice = subscriptions(true, RelayMode.STORE);
    afterChoice.load();
    assertThat(afterChoice.setting())
        .isEqualTo(new RelaySubscriptions.Setting(RelayMode.OFF, List.of("ko")));
    verify(relays, times(3)).save(fresh);
  }

  @Test
  void withNoModeNamedOrRelaysOffTheStoredSettingIsLeftAsItIs() {
    FederationRelaySettingEntity fresh = settingRow(RelayMode.COUNT, null);
    when(relays.setting()).thenReturn(fresh);

    RelaySubscriptions unnamed = subscriptions(true, null);
    unnamed.load();
    assertThat(unnamed.setting().mode()).isEqualTo(RelayMode.COUNT);

    subscriptions(false, RelayMode.STORE).load();
    assertThat(fresh.getMode()).isEqualTo(RelayMode.COUNT);
    verify(relays, never()).save(any(FederationRelaySettingEntity.class));
  }

  @Test
  void stoppingTheStoreTurnsItIntoCountAndLeavesAnyOtherSettingAlone() {
    FederationRelaySettingEntity row = settingRow(RelayMode.COUNT, null);
    when(relays.setting()).thenReturn(row);
    RelaySubscriptions subscriptions = subscriptions(true);
    subscriptions.change(RelayMode.STORE, List.of("ko", "ja"));

    subscriptions.stopStoring();

    assertThat(subscriptions.setting())
        .isEqualTo(new RelaySubscriptions.Setting(RelayMode.COUNT, List.of("ko", "ja")));
    assertThat(row.getMode()).isEqualTo(RelayMode.COUNT);
    assertThat(row.getLanguages()).isEqualTo("ko,ja");
    verify(relays, times(2)).save(row);

    subscriptions.change(RelayMode.OFF, List.of("ko"));
    subscriptions.stopStoring();
    assertThat(subscriptions.setting().mode()).isEqualTo(RelayMode.OFF);
    verify(relays, times(3)).save(row);
  }

  @Test
  void aStoreTheServerWasStartedInStaysStoppedAcrossARestartOnceTheBreakerStoppedIt() {
    FederationRelaySettingEntity row = settingRow(RelayMode.COUNT, null);
    when(relays.setting()).thenReturn(row);
    RelaySubscriptions started = subscriptions(true, RelayMode.STORE);
    started.load();
    assertThat(started.setting().mode()).isEqualTo(RelayMode.STORE);

    started.stopStoring();

    RelaySubscriptions restarted = subscriptions(true, RelayMode.STORE);
    restarted.load();
    assertThat(restarted.setting().mode()).isEqualTo(RelayMode.COUNT);
    assertThat(row.getChosenAt()).isEqualTo(NOW);
  }

  @Test
  void anAcceptNamingAFollowIdOfItsOwnStillAnswersThatRelaysSubscription() {
    FederationRelayEntity pending = row(4L, RelayState.PENDING);
    when(relays.findByFollowActivityId("https://relay.example/follows/theirs"))
        .thenReturn(Optional.empty());
    when(relays.findByActorUri(RELAY)).thenReturn(Optional.of(pending));
    when(relays.list()).thenReturn(List.of(pending));
    RelaySubscriptions subscriptions = subscriptions(true);

    assertThat(subscriptions.answered(relayActor(), "https://relay.example/follows/theirs", true))
        .isTrue();
    assertThat(subscriptions.isAccepted(RELAY)).isTrue();
  }

  @Test
  void unsubscribingSendsUndoAndStopsTheAnnouncesAtOnce() {
    FederationRelayEntity accepted = row(2L, RelayState.ACCEPTED);
    List<FederationRelayEntity> rows = new ArrayList<>(List.of(accepted));
    when(relays.list()).thenAnswer(call -> rows);
    when(relays.findByFollowActivityId(accepted.getFollowActivityId()))
        .thenReturn(Optional.of(accepted));
    when(relays.findById(2L)).thenReturn(Optional.of(accepted));
    RelaySubscriptions subscriptions = subscriptions(true);
    subscriptions.answered(relayActor(), accepted.getFollowActivityId(), true);
    assertThat(subscriptions.isAccepted(RELAY)).isTrue();

    doAnswer(
            call -> {
              rows.remove(accepted);
              return null;
            })
        .when(relays)
        .delete(accepted);
    subscriptions.unsubscribe(2L);

    assertThat(subscriptions.isAccepted(RELAY)).isFalse();
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(
            eq(null),
            eq(accepted.getFollowActivityId() + "/undo"),
            body.capture(),
            eq(List.of(RELAY)));
    JsonNode undo = JSON.readTree(body.getValue());
    assertThat(undo.path("type").asString()).isEqualTo("Undo");
    assertThat(undo.path("object").path("id").asString()).isEqualTo(accepted.getFollowActivityId());
    assertThat(codeOf(() -> subscriptions.unsubscribe(9L)))
        .isEqualTo(FederationErrorCode.RELAY_NOT_FOUND);
  }

  @Test
  void relaysOffMeansNoAnnounceIsEverARelays() {
    RelaySubscriptions off = subscriptions(false);
    off.load();

    assertThat(off.isAccepted(RELAY)).isFalse();
    assertThat(off.setting().mode()).isEqualTo(RelayMode.OFF);
    verifyNoInteractions(relays);
    verify(deliveries, never()).enqueue(any(), anyString(), anyString(), any());
  }
}
