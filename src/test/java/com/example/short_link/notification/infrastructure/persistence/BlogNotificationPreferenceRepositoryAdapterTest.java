package com.example.short_link.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.domain.BlogNotificationPreferenceEntity;
import com.example.short_link.notification.domain.NotificationType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlogNotificationPreferenceRepositoryAdapterTest {

  @Mock private JpaBlogNotificationPreferenceRepository jpa;

  private BlogNotificationPreferenceRepositoryAdapter adapter() {
    return new BlogNotificationPreferenceRepositoryAdapter(jpa);
  }

  @Test
  void delegatesLookupsAndSave() {
    BlogNotificationPreferenceEntity row =
        new BlogNotificationPreferenceEntity(1L, NotificationType.LIKE, false);
    when(jpa.findByUserIdAndType(1L, NotificationType.LIKE)).thenReturn(Optional.of(row));
    when(jpa.findByUserId(1L)).thenReturn(List.of(row));
    when(jpa.save(row)).thenReturn(row);

    assertThat(adapter().findByUserIdAndType(1L, NotificationType.LIKE)).contains(row);
    assertThat(adapter().findByUserId(1L)).containsExactly(row);
    assertThat(adapter().save(row)).isSameAs(row);
  }

  @Test
  void findDisabledUserIdsDelegatesForNonEmptyInput() {
    List<Long> ids = List.of(7L, 8L);
    when(jpa.findDisabledUserIds(ids, NotificationType.NEW_POST)).thenReturn(List.of(8L));

    assertThat(adapter().findDisabledUserIds(ids, NotificationType.NEW_POST)).containsExactly(8L);
  }

  @Test
  void findDisabledUserIdsShortCircuitsEmptyInputWithoutQuery() {
    assertThat(adapter().findDisabledUserIds(List.of(), NotificationType.NEW_POST)).isEmpty();
    verify(jpa, never()).findDisabledUserIds(anyCollection(), any(NotificationType.class));
  }
}
