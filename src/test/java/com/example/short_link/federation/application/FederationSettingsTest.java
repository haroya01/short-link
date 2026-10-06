package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.domain.FederationPreferenceEntity;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.repository.FederationPreferenceRepository;
import com.example.short_link.federation.domain.repository.FederationUserReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FederationSettingsTest {

  @Mock private FederationPreferenceRepository preferences;
  @Mock private FederationUserReader users;
  @Mock private FederationLeaving leaving;

  private FederationSettings settings() {
    return new FederationSettings(
        preferences,
        users,
        leaving,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void withoutARowFederationIsOnAndTheNoticeUnseen() {
    when(preferences.find(7L)).thenReturn(Optional.empty());
    when(users.findActiveById(7L))
        .thenReturn(Optional.of(new FederationUser(7L, "yuki", null, null)));

    assertThat(settings().view(7L))
        .isEqualTo(new FederationSettingsView(true, false, "@yuki@kurl.me"));
    assertThat(settings().isEnabled(7L)).isTrue();
  }

  @Test
  void turningFederationOffLeavesAndSeeingTheNoticeIsRemembered() {
    when(preferences.find(7L)).thenReturn(Optional.empty());
    when(users.findActiveById(7L)).thenReturn(Optional.empty());

    FederationSettingsView view = settings().update(7L, false, true);

    assertThat(view).isEqualTo(new FederationSettingsView(false, true, null));
    verify(preferences).insert(any());
    verify(leaving).leave(7L);
  }

  @Test
  void turningItBackOnOrLeavingItAloneSendsNothing() {
    FederationPreferenceEntity off = new FederationPreferenceEntity(7L);
    off.turnOff();
    when(preferences.find(7L)).thenReturn(Optional.of(off));

    assertThat(settings().update(7L, true, null).enabled()).isTrue();
    assertThat(settings().update(7L, null, false).enabled()).isTrue();
    assertThat(settings().isEnabled(7L)).isTrue();
    verify(leaving, never()).leave(7L);
    verify(preferences, never()).insert(any());
  }
}
