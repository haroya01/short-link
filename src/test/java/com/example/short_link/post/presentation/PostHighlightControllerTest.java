package com.example.short_link.post.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.HighlightRef;
import com.example.short_link.post.application.read.MyHighlightView;
import com.example.short_link.post.application.read.PostHighlightQueryService;
import com.example.short_link.post.application.write.CreateHighlightUseCase;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP 매핑·status·인증 게이트만 — 검증/소유권 규칙은 서비스 단위 테스트가 진짜로 돈다. */
@KurlWebMvcTest(controllers = PostHighlightController.class)
class PostHighlightControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CreateHighlightUseCase createHighlight;
  @MockitoBean private PostHighlightQueryService highlightQuery;

  private static final long USER_ID = 7L;

  @Test
  void createReturns201WithNote() throws Exception {
    when(createHighlight.execute(any()))
        .thenReturn(
            new HighlightRef(
                10L, 0, 0, 0, 5, "abc", Instant.parse("2026-06-12T00:00:00Z"), "여백의 메모"));

    mvc.perform(
            post("/api/v1/posts/5/highlights")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"blockOrder\":0,\"startOffset\":0,\"endOffset\":5,\"quote\":\"abc\",\"note\":\"여백의 메모\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.blockOrder").value(0))
        .andExpect(jsonPath("$.endBlockOrder").value(0))
        .andExpect(jsonPath("$.quote").value("abc"))
        .andExpect(jsonPath("$.note").value("여백의 메모"));
  }

  @Test
  void createMultiBlockEchoesEndBlockOrder() throws Exception {
    when(createHighlight.execute(any()))
        .thenReturn(
            new HighlightRef(10L, 2, 5, 3, 1, "abc", Instant.parse("2026-06-12T00:00:00Z"), null));

    mvc.perform(
            post("/api/v1/posts/5/highlights")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"blockOrder\":2,\"endBlockOrder\":5,\"startOffset\":3,\"endOffset\":1,\"quote\":\"abc\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.blockOrder").value(2))
        .andExpect(jsonPath("$.endBlockOrder").value(5));
  }

  @Test
  void createWithoutNoteIsAllowed() throws Exception {
    when(createHighlight.execute(any()))
        .thenReturn(
            new HighlightRef(10L, 0, 0, 0, 5, "abc", Instant.parse("2026-06-12T00:00:00Z"), null));

    mvc.perform(
            post("/api/v1/posts/5/highlights")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockOrder\":0,\"startOffset\":0,\"endOffset\":5,\"quote\":\"abc\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.note").doesNotExist());
  }

  @Test
  void createRejectsNoteOver500WithValidation() throws Exception {
    String tooLong = "가".repeat(501);

    mvc.perform(
            post("/api/v1/posts/5/highlights")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"blockOrder\":0,\"startOffset\":0,\"endOffset\":5,\"quote\":\"abc\",\"note\":\""
                        + tooLong
                        + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anonymousCreateIs401() throws Exception {
    mvc.perform(
            post("/api/v1/posts/5/highlights")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockOrder\":0,\"startOffset\":0,\"endOffset\":5,\"quote\":\"abc\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void myHighlightsReturnsLibraryWithNote() throws Exception {
    when(highlightQuery.listMine(USER_ID))
        .thenReturn(
            List.of(
                new MyHighlightView(
                    10L,
                    "abc",
                    0,
                    0,
                    "bob",
                    "slug-5",
                    "Title 5",
                    Instant.parse("2026-06-12T00:00:00Z"),
                    "내 메모")));

    mvc.perform(
            get("/api/v1/users/me/highlights")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(10))
        .andExpect(jsonPath("$[0].postSlug").value("slug-5"))
        .andExpect(jsonPath("$[0].note").value("내 메모"));
  }
}
