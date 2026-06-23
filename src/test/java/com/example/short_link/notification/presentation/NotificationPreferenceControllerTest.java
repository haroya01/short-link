package com.example.short_link.notification.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.link.NotificationPreferenceService;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.presentation.request.UpdateNotificationPreferenceRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationPreferenceControllerTest {

  private final NotificationPreferenceService service = mock(NotificationPreferenceService.class);
  private final NotificationPreferenceController controller =
      new NotificationPreferenceController(service);

  @Test
  void listReturnsServiceMap() {
    Map<LinkNotificationType, Boolean> map = Map.of(LinkNotificationType.DIGEST, false);
    when(service.all(9L)).thenReturn(map);
    assertThat(controller.list(9L)).isEqualTo(map);
  }

  @Test
  void updateDelegatesToService() {
    controller.update(
        9L, new UpdateNotificationPreferenceRequest(LinkNotificationType.VELOCITY_SPIKE, false));
    verify(service).setEnabled(9L, LinkNotificationType.VELOCITY_SPIKE, false);
  }
}
