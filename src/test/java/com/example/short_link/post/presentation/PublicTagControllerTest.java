package com.example.short_link.post.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.example.short_link.post.domain.TagCount;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = PublicTagController.class)
class PublicTagControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PublicFeedQueryService publicFeedQueryService;

  @Test
  void listsPopularTagsNoAuthRequired() throws Exception {
    when(publicFeedQueryService.popularTags(50))
        .thenReturn(List.of(new TagCount("spring", 7L), new TagCount("react", 3L)));

    mvc.perform(get("/api/v1/public/tags"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].tag").value("spring"))
        .andExpect(jsonPath("$[0].count").value(7))
        .andExpect(jsonPath("$[1].tag").value("react"));
  }
}
