package com.example.short_link.post.collection.presentation;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
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

  @Test
  void batchReturnsCollectionsPerRequestedPost_inRequestOrder() throws Exception {
    CollectionSummaryView view =
        new CollectionSummaryView(
            10L,
            "느린 사고",
            "오래 머문 글",
            "PUBLIC",
            "COLLECTION",
            4,
            Instant.parse("2026-06-12T00:00:00Z"),
            List.of());
    when(queryService.publicCollectionsContainingBatch(
            ConnectionBlockType.POST, List.of(5L, 6L, 7L)))
        .thenReturn(Map.of(5L, List.of(view), 6L, List.of(), 7L, List.of()));

    // 미로그인(헤더 없음)에도 200 — GET /api/v1/public/** 은 permitAll. 요청 순서대로 세 글이 다 온다.
    mvc.perform(get("/api/v1/public/posts/collections").param("ids", "5", "6", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].postId").value(5))
        .andExpect(jsonPath("$[0].collections[0].id").value(10))
        .andExpect(jsonPath("$[0].collections[0].visibility").value("PUBLIC"))
        .andExpect(jsonPath("$[0].collections[0].count").value(4))
        .andExpect(jsonPath("$[1].postId").value(6))
        .andExpect(jsonPath("$[1].collections.length()").value(0))
        .andExpect(jsonPath("$[2].postId").value(7))
        .andExpect(jsonPath("$[2].collections.length()").value(0));

    verify(queryService)
        .publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of(5L, 6L, 7L));
  }

  @Test
  void batchAcceptsCommaSeparatedIds() throws Exception {
    when(queryService.publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of(5L, 6L)))
        .thenReturn(Map.of(5L, List.of(), 6L, List.of()));

    // ids=5,6 (콤마 한 파라미터) 도 받아들인다.
    mvc.perform(get("/api/v1/public/posts/collections").param("ids", "5,6"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].postId").value(5))
        .andExpect(jsonPath("$[1].postId").value(6));
  }

  @Test
  void batchDeduplicatesAndCapsIds() throws Exception {
    // 51개 중복 없는 id + 중복 → distinct 후 상한 50개만 서비스로 넘어간다.
    String ids =
        IntStream.rangeClosed(1, 51).mapToObj(Integer::toString).collect(Collectors.joining(","));
    List<Long> firstFifty = LongStream.rangeClosed(1, 50).boxed().toList();
    when(queryService.publicCollectionsContainingBatch(eq(ConnectionBlockType.POST), anyList()))
        .thenReturn(Map.of());

    mvc.perform(get("/api/v1/public/posts/collections").param("ids", ids))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(50)); // 51번째는 잘림

    verify(queryService).publicCollectionsContainingBatch(ConnectionBlockType.POST, firstFifty);
  }
}
