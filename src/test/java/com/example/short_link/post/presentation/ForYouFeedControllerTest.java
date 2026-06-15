package com.example.short_link.post.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.ForYouQueryService;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.application.read.PublicFeedItem;
import com.example.short_link.post.application.read.PublicFeedView;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = ForYouFeedController.class)
class ForYouFeedControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private ForYouQueryService forYouQueryService;

  private static final long USER_ID = 9L;

  @Test
  void feedReturnsItems() throws Exception {
    PublicFeedItem item =
        new PublicFeedItem(
            10L,
            new PublicAuthorView(2L, "bob", null, null),
            "slug",
            "Title",
            null,
            null,
            "ko",
            List.of("ai"),
            Instant.parse("2026-01-01T00:00:00Z"),
            0,
            0);
    when(forYouQueryService.feedForYou(USER_ID, 0, 20))
        .thenReturn(new PublicFeedView(List.of(item), 0, 20, false));

    mvc.perform(
            get("/api/v1/feed/for-you").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].title").value("Title"))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void clampsPageAndSize() throws Exception {
    when(forYouQueryService.feedForYou(USER_ID, 0, 50))
        .thenReturn(new PublicFeedView(List.of(), 0, 50, false));

    mvc.perform(
            get("/api/v1/feed/for-you")
                .param("page", "-1")
                .param("size", "999")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk());

    verify(forYouQueryService).feedForYou(USER_ID, 0, 50);
  }
}
