package com.example.short_link.notification.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.dto.NotificationListResult;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationView;
import com.example.short_link.notification.application.read.NotificationQueryService;
import com.example.short_link.notification.application.write.MarkNotificationReadUseCase;
import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = NotificationController.class)
class NotificationControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private NotificationQueryService queryService;
  @MockitoBean private MarkNotificationReadUseCase markReadUseCase;

  private static final long USER_ID = 9L;

  @Test
  void listReturnsNotifications() throws Exception {
    NotificationView view =
        new NotificationView(
            5L,
            NotificationType.LIKE,
            new NotificationActor(2L, "alice", "a.png"),
            new NotificationPostRef(10L, "my-post", "Hi", null),
            null,
            null,
            false,
            Instant.parse("2026-06-07T00:00:00Z"));
    when(queryService.list(eq(USER_ID), isNull(), eq(20)))
        .thenReturn(new NotificationListResult(List.of(view), null, false));

    mvc.perform(
            get("/api/v1/notifications").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].type").value("LIKE"))
        .andExpect(jsonPath("$.items[0].actorUsername").value("alice"))
        .andExpect(jsonPath("$.items[0].postSlug").value("my-post"))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  void listExposesCollectionFieldsForGraphNotice() throws Exception {
    NotificationView view =
        new NotificationView(
            6L,
            NotificationType.CONNECTED,
            new NotificationActor(2L, "alice", "a.png"),
            null,
            null,
            new NotificationCollectionRef(42L, "긴 여름의 독서", 10L),
            false,
            Instant.parse("2026-07-10T00:00:00Z"));
    when(queryService.list(eq(USER_ID), isNull(), eq(20)))
        .thenReturn(new NotificationListResult(List.of(view), null, false));

    mvc.perform(
            get("/api/v1/notifications").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].type").value("CONNECTED"))
        .andExpect(jsonPath("$.items[0].collectionId").value(42))
        .andExpect(jsonPath("$.items[0].collectionName").value("긴 여름의 독서"))
        // The occasioning post surfaces on the shared postId field for a preview.
        .andExpect(jsonPath("$.items[0].postId").value(10))
        .andExpect(jsonPath("$.items[0].postSlug").doesNotExist());
  }

  @Test
  void unreadCountReturnsCount() throws Exception {
    when(queryService.unreadCount(USER_ID)).thenReturn(7L);

    mvc.perform(
            get("/api/v1/notifications/unread-count")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(7));
  }

  @Test
  void markReadReturns204() throws Exception {
    mvc.perform(
            post("/api/v1/notifications/5/read")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());
    verify(markReadUseCase).markRead(USER_ID, 5L);
  }

  @Test
  void readAllReturnsZeroUnread() throws Exception {
    mvc.perform(
            post("/api/v1/notifications/read-all")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(0));
    verify(markReadUseCase).markAllRead(USER_ID);
  }

  @Test
  void anonymousIs401() throws Exception {
    mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
  }
}
