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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = PublicConnectionFeedController.class)
@Import(PostExceptionHandler.class)
class PublicConnectionFeedControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private DiscoverFeedQueryService discoverFeedQuery;

  @Test
  void anonymousGetsGlobalPublicConnectionFeed() throws Exception {
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
    when(discoverFeedQuery.publicFeed(0, 20))
        .thenReturn(new DiscoverFeedView(List.of(item), 0, 20, false, "global"));

    // 미로그인(헤더 없음)에도 200 — GET /api/v1/public/** 은 permitAll.
    mvc.perform(get("/api/v1/public/feed/connections"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].curator.username").value("minji"))
        .andExpect(jsonPath("$.items[0].collectionTitle").value("느린 사고"))
        .andExpect(jsonPath("$.items[0].blockType").value("POST"))
        .andExpect(jsonPath("$.items[0].title").value("헥사고날로 갈아탄 지 석 달"))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.source").value("global"));
  }

  @Test
  void passesPagingParams() throws Exception {
    when(discoverFeedQuery.publicFeed(2, 10))
        .thenReturn(new DiscoverFeedView(List.of(), 2, 10, false, "global"));

    mvc.perform(get("/api/v1/public/feed/connections").param("page", "2").param("size", "10"))
        .andExpect(status().isOk());

    verify(discoverFeedQuery).publicFeed(2, 10);
  }

  // ?size=2000000 같은 익명 요청도 상한(50)으로 묶어 대형 쿼리·팬아웃으로 번지지 않게 한다.
  @Test
  void clampsOversizedPageSize() throws Exception {
    when(discoverFeedQuery.publicFeed(0, 50))
        .thenReturn(new DiscoverFeedView(List.of(), 0, 50, false, "global"));

    mvc.perform(get("/api/v1/public/feed/connections").param("size", "2000000"))
        .andExpect(status().isOk());

    verify(discoverFeedQuery).publicFeed(0, 50);
  }

  @Test
  void emptyFeedReturnsEmptyItems() throws Exception {
    when(discoverFeedQuery.publicFeed(0, 20))
        .thenReturn(new DiscoverFeedView(List.of(), 0, 20, false, "global"));

    mvc.perform(get("/api/v1/public/feed/connections"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.hasNext").value(false));
  }
}
