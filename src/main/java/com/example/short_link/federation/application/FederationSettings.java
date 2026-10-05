package com.example.short_link.federation.application;

import com.example.short_link.federation.domain.FederationPreferenceEntity;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.repository.FederationPreferenceRepository;
import com.example.short_link.federation.domain.repository.FederationUserReader;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FederationSettings {

  private final FederationPreferenceRepository preferences;
  private final FederationUserReader users;
  private final FederationLeaving leaving;
  private final FederationUrls urls;
  private final Clock clock;

  @Autowired
  public FederationSettings(
      FederationPreferenceRepository preferences,
      FederationUserReader users,
      FederationLeaving leaving,
      FederationUrls urls) {
    this(preferences, users, leaving, urls, Clock.systemUTC());
  }

  FederationSettings(
      FederationPreferenceRepository preferences,
      FederationUserReader users,
      FederationLeaving leaving,
      FederationUrls urls,
      Clock clock) {
    this.preferences = preferences;
    this.users = users;
    this.leaving = leaving;
    this.urls = urls;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public FederationSettingsView view(Long userId) {
    return view(userId, preferences.find(userId));
  }

  @Transactional
  public FederationSettingsView update(Long userId, Boolean enabled, Boolean noticeSeen) {
    Optional<FederationPreferenceEntity> stored = preferences.find(userId);
    FederationPreferenceEntity preference =
        stored.orElseGet(() -> new FederationPreferenceEntity(userId));
    boolean wasEnabled = preference.isEnabled();
    if (Boolean.TRUE.equals(enabled)) {
      preference.turnOn();
    } else if (Boolean.FALSE.equals(enabled)) {
      preference.turnOff();
    }
    if (Boolean.TRUE.equals(noticeSeen)) {
      preference.markNoticeSeen(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
    if (stored.isEmpty()) {
      preferences.insert(preference);
    }
    if (wasEnabled && !preference.isEnabled()) {
      leaving.leave(userId);
    }
    return view(userId, Optional.of(preference));
  }

  @Transactional(readOnly = true)
  public boolean isEnabled(Long userId) {
    return preferences.find(userId).map(FederationPreferenceEntity::isEnabled).orElse(true);
  }

  private FederationSettingsView view(
      Long userId, Optional<FederationPreferenceEntity> preference) {
    String handle =
        users
            .findActiveById(userId)
            .map(FederationUser::username)
            .map(username -> "@" + username + "@" + urls.domain())
            .orElse(null);
    return new FederationSettingsView(
        preference.map(FederationPreferenceEntity::isEnabled).orElse(true),
        preference.map(p -> p.getNoticeSeenAt() != null).orElse(false),
        handle);
  }
}
