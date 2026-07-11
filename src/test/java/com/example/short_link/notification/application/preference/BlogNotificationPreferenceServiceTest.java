package com.example.short_link.notification.application.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.domain.BlogNotificationPreferenceEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.BlogNotificationPreferenceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BlogNotificationPreferenceServiceTest {

  private final BlogNotificationPreferenceRepository repo =
      mock(BlogNotificationPreferenceRepository.class);
  private final BlogNotificationPreferenceService service =
      new BlogNotificationPreferenceService(repo);

  @Test
  void absentPreferenceDefaultsToEnabled() {
    when(repo.findByUserIdAndType(1L, NotificationType.LIKE)).thenReturn(Optional.empty());
    assertThat(service.isEnabled(1L, NotificationType.LIKE)).isTrue();
  }

  @Test
  void presentDisabledPreferenceSilences() {
    when(repo.findByUserIdAndType(1L, NotificationType.COMMENT))
        .thenReturn(
            Optional.of(new BlogNotificationPreferenceEntity(1L, NotificationType.COMMENT, false)));
    assertThat(service.isEnabled(1L, NotificationType.COMMENT)).isFalse();
  }

  @Test
  void allReturnsEveryTypeDefaultingAbsentToEnabled() {
    when(repo.findByUserId(1L))
        .thenReturn(
            List.of(new BlogNotificationPreferenceEntity(1L, NotificationType.NEW_POST, false)));

    var all = service.all(1L);

    assertThat(all).hasSize(NotificationType.values().length);
    assertThat(all.get(NotificationType.NEW_POST)).isFalse();
    assertThat(all.get(NotificationType.LIKE)).isTrue();
  }

  @Test
  void setEnabledUpdatesExistingRowInPlace() {
    BlogNotificationPreferenceEntity existing =
        new BlogNotificationPreferenceEntity(1L, NotificationType.FOLLOW, true);
    when(repo.findByUserIdAndType(1L, NotificationType.FOLLOW)).thenReturn(Optional.of(existing));

    service.setEnabled(1L, NotificationType.FOLLOW, false);

    assertThat(existing.isEnabled()).isFalse();
    verify(repo, never()).save(any());
  }

  @Test
  void setEnabledInsertsWhenAbsent() {
    when(repo.findByUserIdAndType(1L, NotificationType.MENTION)).thenReturn(Optional.empty());

    service.setEnabled(1L, NotificationType.MENTION, false);

    verify(repo).save(any(BlogNotificationPreferenceEntity.class));
  }

  @Test
  void filterEnabledReturnsInputUnchangedWhenNobodyMuted() {
    List<Long> ids = List.of(7L, 8L, 9L);
    when(repo.findDisabledUserIds(ids, NotificationType.NEW_POST)).thenReturn(List.of());

    assertThat(service.filterEnabled(ids, NotificationType.NEW_POST)).isSameAs(ids);
  }

  @Test
  void filterEnabledDropsMutedPreservingOrder() {
    List<Long> ids = List.of(7L, 8L, 9L, 10L);
    when(repo.findDisabledUserIds(ids, NotificationType.NEW_POST)).thenReturn(List.of(9L, 7L));

    assertThat(service.filterEnabled(ids, NotificationType.NEW_POST)).containsExactly(8L, 10L);
  }

  @Test
  void filterEnabledShortCircuitsEmptyInputWithoutQuery() {
    assertThat(service.filterEnabled(List.of(), NotificationType.NEW_POST)).isEmpty();
    verify(repo, never()).findDisabledUserIds(anyCollection(), eq(NotificationType.NEW_POST));
  }
}
