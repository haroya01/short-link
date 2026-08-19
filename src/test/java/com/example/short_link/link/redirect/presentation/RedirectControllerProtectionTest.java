package com.example.short_link.link.redirect.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RedirectControllerProtectionTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository repository;

  @Test
  void passwordProtectedLinkShowsPromptOnGet() throws Exception {
    LinkEntity link = repository.save(new LinkEntity("https://example.com", "pwd0001"));
    LinkEntity reloaded = repository.findByShortCode(new ShortCode("pwd0001")).orElseThrow();
    reloaded.setPasswordHash("$2a$10$dummyhashvalueforbcrypt000000000000000000000000000000");
    repository.save(reloaded);

    mvc.perform(get("/pwd0001"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(Matchers.containsString("password")));
  }

  /**
   * 스푸핑된 크롤러 UA 가 비밀번호 보호를 우회하지 못한다 — 미리보기 분기가 비밀번호 게이트보다 먼저 돌면 목적지가 OG 카드로 통째로 노출됐다(캐시 300초로
   * 재배포까지). 크롤러도 프롬프트만 받고, 목적지 URL 은 어디에도 실리지 않는다.
   */
  @Test
  void crawlerCannotBypassPasswordToLeakDestination() throws Exception {
    repository.save(new LinkEntity("https://secret-destination.example.com/private", "pwd0009"));
    LinkEntity stored = repository.findByShortCode(new ShortCode("pwd0009")).orElseThrow();
    stored.setPasswordHash("$2a$10$dummyhashvalueforbcrypt000000000000000000000000000000");
    repository.save(stored);

    mvc.perform(get("/pwd0009").header("User-Agent", "Twitterbot/1.0"))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("password")))
        .andExpect(content().string(Matchers.not(Matchers.containsString("secret-destination"))));
  }

  @Test
  void unlockWithWrongPasswordReprompts() throws Exception {
    repository.save(new LinkEntity("https://example.com", "pwd0002"));
    LinkEntity stored = repository.findByShortCode(new ShortCode("pwd0002")).orElseThrow();
    stored.setPasswordHash("$2a$10$nKaXIa.8E2GfzG6dF2lzYOXa0lI6w0aiK8q5BWlBgEd9j3KMRPj7m");
    repository.save(stored);

    mvc.perform(
            post("/pwd0002")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("password=wrong"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredLinkWithCustomMessageReturnsHtml410() throws Exception {
    repository.save(new LinkEntity("https://example.com", "exp0001"));
    LinkEntity stored = repository.findByShortCode(new ShortCode("exp0001")).orElseThrow();
    stored.changeExpiresAt(Instant.now().minusSeconds(60));
    stored.updateExpiredMessage("Sale ended. <script>alert('xss')</script>");
    repository.save(stored);

    String body =
        mvc.perform(get("/exp0001"))
            .andExpect(status().isGone())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // HTML escape — script tag should be encoded, not raw
    assertThat(body).doesNotContain("<script>");
    assertThat(body).contains("&lt;script&gt;");
  }
}
