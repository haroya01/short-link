package com.example.short_link.notification.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.preference.BlogNotificationPreferenceService;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.presentation.request.UpdateBlogNotificationPreferenceRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlogNotificationPreferenceControllerTest {

  private final BlogNotificationPreferenceService service =
      mock(BlogNotificationPreferenceService.class);
  private final BlogNotificationPreferenceController controller =
      new BlogNotificationPreferenceController(service);

  @Test
  void listReturnsServiceMap() {
    Map<NotificationType, Boolean> map = Map.of(NotificationType.NEW_POST, false);
    when(service.all(9L)).thenReturn(map);
    assertThat(controller.list(9L)).isEqualTo(map);
  }

  @Test
  void updateDelegatesToService() {
    controller.update(
        9L, new UpdateBlogNotificationPreferenceRequest(NotificationType.LIKE, false));
    verify(service).setEnabled(9L, NotificationType.LIKE, false);
  }
}
