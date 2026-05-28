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
