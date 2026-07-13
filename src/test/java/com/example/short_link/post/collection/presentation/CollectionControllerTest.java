package com.example.short_link.post.collection.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.collection.application.read.CollectionDetailView;
import com.example.short_link.post.collection.application.read.CollectionQueryService;
import com.example.short_link.post.collection.application.read.CollectionSummaryView;
import com.example.short_link.post.collection.application.read.ConnectionView;
import com.example.short_link.post.collection.application.write.CollectionCommandService;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.CollectionKind;
import com.example.short_link.post.collection.domain.CollectionVisibility;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.presentation.PostExceptionHandler;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP 매핑·status·인증 게이트만 — 검증/소유권/멱등 규칙은 서비스 단위 테스트가 진짜로 돈다. */
@KurlWebMvcTest(controllers = CollectionController.class)
@Import(PostExceptionHandler.class)
class CollectionControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CollectionCommandService command;
  @MockitoBean private CollectionQueryService query;

  private static final long USER_ID = 7L;

  private static CollectionEntity collection(long id) {
    CollectionEntity c =
        new CollectionEntity(
            USER_ID, "느린 사고", "오래 머문 글", CollectionVisibility.PUBLIC, CollectionKind.COLLECTION);
    ReflectionTestUtils.setField(c, "id", id);
    ReflectionTestUtils.setField(c, "updatedAt", Instant.parse("2026-06-12T00:00:00Z"));
    return c;
  }

  @Test
  void createReturns201WithSummary() throws Exception {
    when(command.create(any())).thenReturn(collection(10L));

    mvc.perform(
            post("/api/v1/collections")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"느린 사고\",\"visibility\":\"PUBLIC\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.title").value("느린 사고"))
        .andExpect(jsonPath("$.visibility").value("PUBLIC"))
        .andExpect(jsonPath("$.count").value(0));
  }

  @Test
  void createRejectsBlankTitleWithValidation() throws Exception {
    mvc.perform(
            post("/api/v1/collections")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"visibility\":\"PUBLIC\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anonymousCreateIs401() throws Exception {
    mvc.perform(
            post("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"느린 사고\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void editReturnsUpdatedSummary() throws Exception {
    when(command.edit(any())).thenReturn(collection(10L));
    when(query.connectionCount(10L)).thenReturn(2L);

    mvc.perform(
            put("/api/v1/collections/10")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"느린 사고\",\"visibility\":\"PUBLIC\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.count").value(2));
  }

  @Test
  void editRejectsBlankTitleWithValidation() throws Exception {
    mvc.perform(
            put("/api/v1/collections/10")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void myCollectionsReturnsList() throws Exception {
    when(query.listMine(USER_ID, null, null))
        .thenReturn(
            List.of(
                new CollectionSummaryView(
                    10L,
                    "느린 사고",
                    "오래 머문 글",
                    "PUBLIC",
                    "COLLECTION",
                    3,
                    Instant.parse("2026-06-12T00:00:00Z"),
                    List.of("헥사고날로 갈아탄 지 석 달", "좋은 추상은 더 지울 게 없을 때"),
                    "curator",
                    "https://cdn.kurl.me/a.jpg",
                    null,
                    null)));

    mvc.perform(
            get("/api/v1/users/me/collections")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(10))
        .andExpect(jsonPath("$[0].count").value(3))
        .andExpect(jsonPath("$[0].preview[0]").value("헥사고날로 갈아탄 지 석 달"));
  }

  @Test
  void myCollectionsWithBlockParamsCarriesConnectionId() throws Exception {
    when(query.listMine(USER_ID, ConnectionBlockType.POST, 77L))
        .thenReturn(
            List.of(
                new CollectionSummaryView(
                    10L,
                    "느린 사고",
                    null,
                    "PUBLIC",
                    "COLLECTION",
                    3,
                    Instant.parse("2026-06-12T00:00:00Z"),
                    List.of(),
                    "curator",
                    null,
                    null,
                    501L)));

    // 연결 시트가 "이미 담김"을 그리도록 — 블록 기준 조회에 그 연결의 PK 가 실린다.
    mvc.perform(
            get("/api/v1/users/me/collections")
                .param("blockType", "POST")
                .param("refId", "77")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].connectionId").value(501));
  }

  @Test
  void detailReturnsResolvedConnections() throws Exception {
    ConnectionView post =
        ConnectionView.post(
            100L, "왜", Instant.parse("2026-06-12T00:00:00Z"), "글 제목", "발췌", "slug", "alice");
    when(query.detail(USER_ID, 10L))
        .thenReturn(
            new CollectionDetailView(
                10L, "느린 사고", "오래 머문 글", "PUBLIC", "COLLECTION", "curator", List.of(post)));

    mvc.perform(
            get("/api/v1/collections/10").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.curatorUsername").value("curator"))
        .andExpect(jsonPath("$.connections[0].blockType").value("POST"))
        .andExpect(jsonPath("$.connections[0].title").value("글 제목"));
  }

  @Test
  void detailMissingMapsToNotFoundEnvelope() throws Exception {
    when(query.detail(USER_ID, 99L))
        .thenThrow(new PostException(PostErrorCode.COLLECTION_NOT_FOUND, 99L));

    mvc.perform(
            get("/api/v1/collections/99").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("COLLECTION_NOT_FOUND"));
  }

  @Test
  void connectReturns201AndPassesCommand() throws Exception {
    mvc.perform(
            post("/api/v1/collections/10/connections")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockType\":\"POST\",\"refId\":5,\"why\":\"좋은 글\"}"))
        .andExpect(status().isCreated());

    verify(command).connect(any());
  }

  @Test
  void connectRejectsMissingBlockType() throws Exception {
    mvc.perform(
            post("/api/v1/collections/10/connections")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refId\":5}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void disconnectReturns204() throws Exception {
    mvc.perform(
            delete("/api/v1/collections/10/connections/33")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());

    verify(command).disconnect(USER_ID, 10L, 33L);
  }

  @Test
  void reorderConnectionsReturns204AndPassesOrder() throws Exception {
    mvc.perform(
            put("/api/v1/collections/10/connections/order")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectionIds\":[103,101,102]}"))
        .andExpect(status().isNoContent());

    verify(command).reorder(USER_ID, 10L, List.of(103L, 101L, 102L));
  }

  @Test
  void reorderRejectsEmptyList() throws Exception {
    mvc.perform(
            put("/api/v1/collections/10/connections/order")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectionIds\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteReturns204() throws Exception {
    mvc.perform(
            delete("/api/v1/collections/10")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());

    verify(command).deleteCollection(USER_ID, 10L);
  }

  @Test
  void anonymousMyCollectionsIs401() throws Exception {
    mvc.perform(get("/api/v1/users/me/collections")).andExpect(status().isUnauthorized());
  }
}
