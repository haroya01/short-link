package com.example.short_link.link.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 프로드 구도의 base-url 로 자기참조 거부를 검증한다 — apex(kurl.me)와 www 변형만 막고,
// blog.kurl.me 같은 콘텐츠 서브도메인은 그대로 단축돼야 한다.
@SpringBootTest(properties = "short-link.base-url=https://kurl.me")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SelfReferenceUrlTest {

  @Autowired private MockMvc mvc;

  @Test
  void rejectsReShorteningOwnShortLink() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://kurl.me/abc123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SELF_REFERENCING_URL"));
  }

  @Test
  void rejectsWwwVariant() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://www.kurl.me/promo\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SELF_REFERENCING_URL"));
  }

  @Test
  void allowsBlogSubdomainContent() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://blog.kurl.me/haroya/some-post\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void allowsForeignUrl() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/path\"}"))
        .andExpect(status().isCreated());
  }
}
