package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.domain.FederationFollowingEntity;
import com.example.short_link.federation.domain.OnRemoteAccount;
import com.example.short_link.federation.domain.RemoteAccountStanding;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.RemoteMuteEntity;
import com.example.short_link.federation.domain.repository.FederationFollowingRepository;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import com.example.short_link.federation.domain.repository.RemoteMuteRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RemoteMutesTest {

  private static final Instant NOW = Instant.parse("2026-10-15T00:00:00.123456789Z");
  private static final Instant NOW_MICROS = Instant.parse("2026-10-15T00:00:00.123456Z");

  @Mock private RemoteMuteRepository mutes;
  @Mock private RemoteAccountStandingReader standings;
  @Mock private FederationFollowingRepository followings;
  @Mock private RemoteFollowing following;

  private RemoteActorEntity alice;
  private final RemoteAccountStanding plain = RemoteAccountStanding.NONE;

  @BeforeEach
  void aliceIsKnown() {
    alice = actor(42L, "alice", "mastodon.example");
    lenient().when(following.remote(42L)).thenReturn(alice);
    lenient().when(following.known(42L)).thenReturn(alice);
    lenient().when(following.standing(7L, alice)).thenReturn(plain);
  }

  private static RemoteActorEntity actor(Long id, String username, String domain) {
    String uri = "https://" + domain + "/users/" + username;
    RemoteActorEntity actor =
        new RemoteActorEntity(
            new RemoteActorDocument(
                uri, uri + "#k", "pem", uri + "/inbox", null, username, domain, null, null, null),
            NOW);
    ReflectionTestUtils.setField(actor, "id", id);
    return actor;
  }

  private RemoteMutes service() {
    return new RemoteMutes(
        mutes, standings, followings, following, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void mutingForAWhileWithoutNoticesKeepsBoth() {
    when(mutes.find(7L, 42L)).thenReturn(Optional.empty());
    RemoteAccountView expected =
        RemoteAccountView.of(alice, Optional.empty(), plain.withMuting(true));
    when(following.view(7L, alice, plain.withMuting(true))).thenReturn(expected);

    assertThat(service().mute(7L, 42L, false, 3600L)).isSameAs(expected);

    ArgumentCaptor<RemoteMuteEntity> saved = ArgumentCaptor.forClass(RemoteMuteEntity.class);
    verify(mutes).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(7L);
    assertThat(saved.getValue().getRemoteActorId()).isEqualTo(42L);
    assertThat(saved.getValue().isHideNotifications()).isFalse();
    assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW_MICROS.plusSeconds(3600));
  }

  @Test
  void aMuteWithNoBodyHidesNoticesAndLastsForever() {
    when(mutes.find(7L, 42L)).thenReturn(Optional.empty());

    service().mute(7L, 42L, null, null);

    ArgumentCaptor<RemoteMuteEntity> saved = ArgumentCaptor.forClass(RemoteMuteEntity.class);
    verify(mutes).save(saved.capture());
    assertThat(saved.getValue().isHideNotifications()).isTrue();
    assertThat(saved.getValue().getExpiresAt()).isNull();
  }

  @Test
  void mutingAgainChangesTheMuteInPlace() {
    RemoteMuteEntity existing = new RemoteMuteEntity(7L, 42L, true, null);
    when(mutes.find(7L, 42L)).thenReturn(Optional.of(existing));

    service().mute(7L, 42L, false, 60L);

    verify(mutes, never()).save(any());
    assertThat(existing.isHideNotifications()).isFalse();
    assertThat(existing.getExpiresAt()).isEqualTo(NOW_MICROS.plusSeconds(60));
  }

  @Test
  void aMuteLastsFromAMinuteToAYear() {
    for (long seconds : new long[] {59L, RemoteMuteEntity.MAX_DURATION_SECONDS + 1}) {
      assertThatThrownBy(() -> service().mute(7L, 42L, true, seconds))
          .isInstanceOfSatisfying(
              FederationException.class,
              e -> assertThat(e.errorCode()).isEqualTo(FederationErrorCode.MUTE_DURATION_INVALID));
    }
    verifyNoInteractions(mutes);

    when(mutes.find(7L, 42L)).thenReturn(Optional.empty());
    service().mute(7L, 42L, true, RemoteMuteEntity.MAX_DURATION_SECONDS);
    verify(mutes).save(any());
  }

  @Test
  void unmutingDropsTheMuteAndAnswersTheAccountUnmuted() {
    RemoteMuteEntity existing = new RemoteMuteEntity(7L, 42L, true, null);
    when(mutes.find(7L, 42L)).thenReturn(Optional.of(existing), Optional.empty());
    RemoteAccountView expected = RemoteAccountView.of(alice, Optional.empty(), plain);
    when(following.view(7L, alice, plain.withMuting(false))).thenReturn(expected);

    assertThat(service().unmute(7L, 42L)).isSameAs(expected);
    service().unmute(7L, 42L);

    verify(mutes).delete(existing);
  }

  @Test
  void theMuteListCarriesEachEndAndNoticeChoiceWithTheAccountsFollow() {
    RemoteActorEntity bob = actor(43L, "bob", "social.example");
    Instant ends = NOW.plusSeconds(600);
    when(mutes.active(7L, NOW, 0, 20))
        .thenReturn(
            List.of(
                new OnRemoteAccount<>(new RemoteMuteEntity(7L, 42L, true, null), alice),
                new OnRemoteAccount<>(new RemoteMuteEntity(7L, 43L, false, ends), bob)));
    when(standings.of(7L, List.of(42L, 43L), NOW))
        .thenReturn(Map.of(42L, new RemoteAccountStanding(false, false, true)));
    FederationFollowingEntity follow = new FederationFollowingEntity(7L, 43L, "f");
    follow.accept(NOW);
    when(followings.findAll(7L, List.of(42L, 43L))).thenReturn(List.of(follow));

    List<RemoteMuteView> page = service().mutes(7L, 0, 20);

    assertThat(page).hasSize(2);
    assertThat(page.get(0).account().id()).isEqualTo(42L);
    assertThat(page.get(0).account().muting()).isTrue();
    assertThat(page.get(0).account().following()).isFalse();
    assertThat(page.get(0).expiresAt()).isNull();
    assertThat(page.get(0).notifications()).isTrue();
    assertThat(page.get(1).account().id()).isEqualTo(43L);
    assertThat(page.get(1).account().muting()).isTrue();
    assertThat(page.get(1).account().following()).isTrue();
    assertThat(page.get(1).expiresAt()).isEqualTo(ends);
    assertThat(page.get(1).notifications()).isFalse();
  }
}
