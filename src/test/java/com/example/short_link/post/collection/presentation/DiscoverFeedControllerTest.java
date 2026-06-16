package com.example.short_link.post.collection.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.collection.application.read.DiscoverConnectionView;
import com.example.short_link.post.collection.application.read.DiscoverFeedQueryService;
import com.example.short_link.post.collection.application.read.DiscoverFeedView;
import com.example.short_link.post.presentation.PostExceptionHandler;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = DiscoverFeedController.class)
@Import(PostExceptionHandler.class)
class DiscoverFeedControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private DiscoverFeedQueryService discoverFeedQuery;

  private static final long USER_ID = 7L;

  @Test
  void returnsCuratorConnectionFeed() throws Exception {
    DiscoverConnectionView item =
        new DiscoverConnectionView(
            100L,
            new PublicAuthorView(2L, "minji", null, null),
            50L,
            "느린 사고",
            "COLLECTION",
            "두고두고 다시 본다.",
            Instant.parse("2026-06-12T00:00:00Z"),
            "POST",
            "헥사고날로 갈아탄 지 석 달",
            "결론부터 적는다.",
            "hexagonal",
            "honggildong",
            null,
            null);
    when(discoverFeedQuery.feed(USER_ID, 0, 20))
        .thenReturn(new DiscoverFeedView(List.of(item), 0, 20, false));

    mvc.perform(
            get("/api/v1/feed/connections")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].curator.username").value("minji"))
        .andExpect(jsonPath("$.items[0].collectionTitle").value("느린 사고"))
        .andExpect(jsonPath("$.items[0].blockType").value("POST"))
        .andExpect(jsonPath("$.items[0].title").value("헥사고날로 갈아탄 지 석 달"))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void passesPagingParams() throws Exception {
    when(discoverFeedQuery.feed(USER_ID, 2, 10))
        .thenReturn(new DiscoverFeedView(List.of(), 2, 10, false));

    mvc.perform(
            get("/api/v1/feed/connections")
                .param("page", "2")
                .param("size", "10")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk());

    verify(discoverFeedQuery).feed(USER_ID, 2, 10);
  }

  @Test
  void anonymousIs401() throws Exception {
    mvc.perform(get("/api/v1/feed/connections")).andExpect(status().isUnauthorized());
  }
}
