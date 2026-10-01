package com.example.short_link.link.redirect.presentation;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.moderation.application.LinkModerationService;
import com.example.short_link.link.moderation.domain.LinkDisableReason;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RedirectControllerDisabledLinkTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository repository;
  @Autowired private LinkModerationService moderation;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void clearCaches() {
    clear(cacheManager, "link");
  }

  @Test
  void aDisabledLinkShowsTheSwitchedOffPageInsteadOfRedirecting() throws Exception {
    LinkEntity link = repository.save(new LinkEntity("https://phish.example/login", "off1234"));
    moderation.disable(link.getId(), LinkDisableReason.ABUSE_REPORT, null);

    mvc.perform(get("/off1234").header("Accept-Language", "ko-KR"))
        .andExpect(status().isGone())
        .andExpect(content().string(Matchers.containsString("꺼진 링크")))
        .andExpect(content().string(Matchers.not(Matchers.containsString("phish.example"))));
  }

  @Test
  void crawlersGetTheSameSwitchedOffPageAndNoPreview() throws Exception {
    LinkEntity link = repository.save(new LinkEntity("https://phish.example/login", "off2345"));
    moderation.disable(link.getId(), LinkDisableReason.SAFE_BROWSING, null);

    mvc.perform(
            get("/off2345")
                .header("User-Agent", "facebookexternalhit/1.1")
                .header("Accept-Language", "en"))
        .andExpect(status().isGone())
        .andExpect(content().string(Matchers.containsString("switched off")))
        .andExpect(content().string(Matchers.not(Matchers.containsString("phish.example"))));
  }
}
