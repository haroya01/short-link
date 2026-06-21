package com.example.short_link.post.collection.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.collection.application.read.CurationGraphQueryService;
import com.example.short_link.post.collection.application.read.KindredCuratorView;
import com.example.short_link.post.collection.application.read.RelatedBlockView;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.presentation.PostExceptionHandler;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = PublicCurationGraphController.class)
@Import(PostExceptionHandler.class)
class PublicCurationGraphControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CurationGraphQueryService graph;

  @Test
  void relatedResolvesLowercaseBlockType() throws Exception {
    when(graph.relatedTo(ConnectionBlockType.HIGHLIGHT, 9L, 12))
        .thenReturn(
            List.of(
                new RelatedBlockView(
                    "POST",
                    5L,
                    "헥사고날로 갈아탄 지 석 달",
                    "결론부터.",
                    "hexagonal",
                    "honggildong",
                    null,
                    null,
                    3)));

    mvc.perform(get("/api/v1/public/graph/blocks/highlight/9/related"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].blockType").value("POST"))
        .andExpect(jsonPath("$[0].refId").value(5))
        .andExpect(jsonPath("$[0].sharedCount").value(3));
  }

  @Test
  void relatedUnknownBlockTypeYieldsEmpty() throws Exception {
    // parseType 가 모르는 종류 → null → 조용히 빈 결과(§0). 서비스는 호출되지 않는다.
    mvc.perform(get("/api/v1/public/graph/blocks/banana/9/related"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void kindredReturnsOverlappingCurators() throws Exception {
    when(graph.kindredCurators("honggildong", 12))
        .thenReturn(
            List.of(
                new KindredCuratorView(new PublicAuthorView(2L, "minji", "취향이 겹치는 사람", null), 4)));

    mvc.perform(get("/api/v1/public/profiles/honggildong/kindred"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].curator.username").value("minji"))
        .andExpect(jsonPath("$[0].sharedItems").value(4));
  }

  @Test
  void relatedHonoursLimitParam() throws Exception {
    when(graph.relatedTo(ConnectionBlockType.POST, 7L, 5)).thenReturn(List.of());

    mvc.perform(get("/api/v1/public/graph/blocks/POST/7/related").param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
