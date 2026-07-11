package com.example.short_link.notification.application.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.notification.BlogNotificationKind;
import com.example.short_link.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;

class BlogNotificationMuteProviderTest {

  private final BlogNotificationPreferenceService service =
      mock(BlogNotificationPreferenceService.class);
  private final BlogNotificationMuteProvider provider = new BlogNotificationMuteProvider(service);

  @Test
  void commentKindMapsToCommentTypeAndInvertsEnabled() {
    when(service.isEnabled(5L, NotificationType.COMMENT)).thenReturn(false);
    assertThat(provider.isMuted(5L, BlogNotificationKind.COMMENT)).isTrue();
  }

  @Test
  void replyKindMapsToReplyTypeAndInvertsEnabled() {
    when(service.isEnabled(5L, NotificationType.REPLY)).thenReturn(true);
    assertThat(provider.isMuted(5L, BlogNotificationKind.REPLY)).isFalse();
  }
}
