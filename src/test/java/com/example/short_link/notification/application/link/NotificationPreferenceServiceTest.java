package com.example.short_link.notification.application.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationPreferenceEntity;
import com.example.short_link.notification.domain.repository.NotificationPreferenceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationPreferenceServiceTest {

  private final NotificationPreferenceRepository repo =
      mock(NotificationPreferenceRepository.class);
  private final NotificationPreferenceService service = new NotificationPreferenceService(repo);

  @Test
  void absentPreferenceDefaultsToEnabled() {
    when(repo.findByUserIdAndType(1L, LinkNotificationType.MILESTONE)).thenReturn(Optional.empty());
    assertThat(service.isEnabled(1L, LinkNotificationType.MILESTONE)).isTrue();
  }

  @Test
  void presentDisabledPreferenceSilences() {
    when(repo.findByUserIdAndType(1L, LinkNotificationType.DIGEST))
        .thenReturn(
            Optional.of(new NotificationPreferenceEntity(1L, LinkNotificationType.DIGEST, false)));
    assertThat(service.isEnabled(1L, LinkNotificationType.DIGEST)).isFalse();
  }

  @Test
  void allReturnsEveryTypeDefaultingAbsentToEnabled() {
    when(repo.findByUserId(1L))
        .thenReturn(
            List.of(
                new NotificationPreferenceEntity(1L, LinkNotificationType.VELOCITY_SPIKE, false)));

    var all = service.all(1L);

    assertThat(all).hasSize(LinkNotificationType.values().length);
    assertThat(all.get(LinkNotificationType.VELOCITY_SPIKE)).isFalse();
    assertThat(all.get(LinkNotificationType.FIRST_CLICK)).isTrue();
  }

  @Test
  void setEnabledUpdatesExistingRowInPlace() {
    NotificationPreferenceEntity existing =
        new NotificationPreferenceEntity(1L, LinkNotificationType.FIRST_CLICK, true);
    when(repo.findByUserIdAndType(1L, LinkNotificationType.FIRST_CLICK))
        .thenReturn(Optional.of(existing));

    service.setEnabled(1L, LinkNotificationType.FIRST_CLICK, false);

    assertThat(existing.isEnabled()).isFalse();
    verify(repo, never()).save(any());
  }

  @Test
  void setEnabledInsertsWhenAbsent() {
    when(repo.findByUserIdAndType(1L, LinkNotificationType.EXPIRY_IMMINENT))
        .thenReturn(Optional.empty());

    service.setEnabled(1L, LinkNotificationType.EXPIRY_IMMINENT, false);

    verify(repo).save(any(NotificationPreferenceEntity.class));
  }
}
