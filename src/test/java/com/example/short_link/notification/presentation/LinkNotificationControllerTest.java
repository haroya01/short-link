package com.example.short_link.notification.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.notification.application.dto.LinkNotificationListResult;
import com.example.short_link.notification.application.dto.LinkNotificationView;
import com.example.short_link.notification.application.read.LinkNotificationQueryService;
import com.example.short_link.notification.application.write.MarkLinkNotificationReadUseCase;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = LinkNotificationController.class)
class LinkNotificationControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private LinkNotificationQueryService queryService;
  @MockitoBean private MarkLinkNotificationReadUseCase markReadUseCase;

  private static final long USER_ID = 9L;

  @Test
  void listReturnsLinkNotifications() throws Exception {
    LinkNotificationView view =
        new LinkNotificationView(
            5L,
            LinkNotificationType.FIRST_CLICK,
            "spring",
            "/spring",
            "첫 클릭이 들어왔어요",
            false,
            Instant.parse("2026-06-25T00:00:00Z"));
    when(queryService.list(eq(USER_ID), isNull(), eq(20)))
        .thenReturn(new LinkNotificationListResult(List.of(view), null, false));

    mvc.perform(
            get("/api/v1/links/notifications")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].type").value("FIRST_CLICK"))
        .andExpect(jsonPath("$.items[0].shortCode").value("spring"))
        .andExpect(jsonPath("$.items[0].read").value(false))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  void unreadCountReturnsCount() throws Exception {
    when(queryService.unreadCount(USER_ID)).thenReturn(7L);

    mvc.perform(
            get("/api/v1/links/notifications/unread-count")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(7));
  }

  @Test
  void markReadReturns204() throws Exception {
    mvc.perform(
            post("/api/v1/links/notifications/5/read")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());
    verify(markReadUseCase).markRead(USER_ID, 5L);
  }

  @Test
  void markAllReadReturnsZero() throws Exception {
    mvc.perform(
            post("/api/v1/links/notifications/read-all")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(0));
    verify(markReadUseCase).markAllRead(USER_ID);
  }
}
