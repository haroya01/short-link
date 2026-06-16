package com.example.short_link.post.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.HighlightReplyView;
import com.example.short_link.post.application.read.PostHighlightReplyQueryService;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 인증 없이 하이라이트 답글 목록 조회. permitAll 는 GET /api/v1/public/** 가 커버. */
@KurlWebMvcTest(controllers = PublicHighlightReplyController.class)
class PublicHighlightReplyControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PostHighlightReplyQueryService replyQuery;

  @Test
  void listReturnsAttributedReplies() throws Exception {
    when(replyQuery.listForHighlight(5L))
        .thenReturn(
            List.of(
                new HighlightReplyView(
                    10L,
                    new PublicAuthorView(1L, "alice", null, null),
                    "좋은 지적이에요",
                    Instant.parse("2026-06-12T00:00:00Z"))));

    mvc.perform(get("/api/v1/public/highlights/5/replies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(10))
        .andExpect(jsonPath("$[0].author.username").value("alice"))
        .andExpect(jsonPath("$[0].body").value("좋은 지적이에요"));
  }
}
