package com.example.short_link.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BodySizeFilterTest {

  @Autowired private MockMvc mvc;

  @Test
  void smallBodyPassesThrough() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void bodyExceeding16KbReturns413WithProblemDetail() throws Exception {
    String oversize = "a".repeat(17 * 1024);
    String body = "{\"url\":\"https://example.com\",\"note\":\"" + oversize + "\"}";

    mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().contentType("application/problem+json"))
        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
        .andExpect(jsonPath("$.detail").value("request body exceeds 16KB limit"));
  }

  // 확장 한도 라우트는 인증 뒤에 앉아 있어 MockMvc 로는 401 이 먼저 난다 — 필터를 직접 호출해
  // 경로별 한도 자체를 검증한다.
  @Test
  void blockEditorRouteAcceptsBodyAboveDefaultCap() throws Exception {
    BodySizeFilter filter = new BodySizeFilter(JsonMapper.builder().build());
    MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/api/v1/posts/1/blocks");
    req.setContent(new byte[64 * 1024]);
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(res.getStatus()).isNotEqualTo(413);
  }

  @Test
  void blockEditorRouteRejectsBodyAboveItsOwnCap() throws Exception {
    BodySizeFilter filter = new BodySizeFilter(JsonMapper.builder().build());
    MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/api/v1/posts/1/blocks");
    req.setContent(new byte[1024 * 1024 + 1]);
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, new MockFilterChain());

    assertThat(res.getStatus()).isEqualTo(413);
    assertThat(res.getContentAsString()).contains("request body exceeds 1024KB limit");
  }

  @Test
  void stripeWebhookRouteAcceptsLargeEvent() throws Exception {
    BodySizeFilter filter = new BodySizeFilter(JsonMapper.builder().build());
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/billing/webhook");
    req.setContent(new byte[100 * 1024]);
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(res.getStatus()).isNotEqualTo(413);
  }
}
