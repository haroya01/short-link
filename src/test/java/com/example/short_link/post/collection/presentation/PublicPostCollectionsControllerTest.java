package com.example.short_link.post.collection.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.collection.application.read.CollectionQueryService;
import com.example.short_link.post.collection.application.read.CollectionSummaryView;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.presentation.PostExceptionHandler;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = PublicPostCollectionsController.class)
@Import(PostExceptionHandler.class)
class PublicPostCollectionsControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CollectionQueryService queryService;

  @Test
  void anonymousGetsPublicCollectionsForPost() throws Exception {
    when(queryService.publicCollectionsContaining(ConnectionBlockType.POST, 5L))
        .thenReturn(
            List.of(
                new CollectionSummaryView(
                    10L,
                    "느린 사고",
                    "오래 머문 글",
                    "PUBLIC",
                    "COLLECTION",
                    4,
                    Instant.parse("2026-06-12T00:00:00Z"),
                    List.of())));

    // 미로그인(헤더 없음)에도 200 — GET /api/v1/public/** 은 permitAll. 글 타입으로 조회된다.
    mvc.perform(get("/api/v1/public/posts/5/collections"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(10))
        .andExpect(jsonPath("$[0].title").value("느린 사고"))
        .andExpect(jsonPath("$[0].visibility").value("PUBLIC"))
        .andExpect(jsonPath("$[0].count").value(4));

    verify(queryService).publicCollectionsContaining(ConnectionBlockType.POST, 5L);
  }

  @Test
  void returnsEmptyWhenPostInNoPublicCollection() throws Exception {
    when(queryService.publicCollectionsContaining(ConnectionBlockType.POST, 99L))
        .thenReturn(List.of());

    mvc.perform(get("/api/v1/public/posts/99/collections"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
