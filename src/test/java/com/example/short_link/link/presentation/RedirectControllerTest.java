package com.example.short_link.link.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RedirectControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository repository;
  @Autowired private ClickEventRepository clickRepository;

  @Test
  void redirectsToOriginalUrl() throws Exception {
    repository.save(new LinkEntity("https://example.com/destination", "abc1234"));

    mvc.perform(get("/abc1234"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/destination"))
        .andExpect(header().string("Cache-Control", "private, max-age=90"))
        .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
  }

  @Test
  void redirectsForCustomCodeShorterThanSeven() throws Exception {
    repository.save(new LinkEntity("https://example.com/short", "myx"));

    mvc.perform(get("/myx"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/short"));
  }

  @Test
  void redirectsForCustomCodeLongerThanSeven() throws Exception {
    repository.save(new LinkEntity("https://example.com/long", "campaign2026"));

    mvc.perform(get("/campaign2026"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/long"));
  }

  @Test
  void recordsClickEventOnRedirect() throws Exception {
    LinkEntity link = repository.save(new LinkEntity("https://example.com", "click01"));

    mvc.perform(
            get("/click01")
                .header("Referer", "https://instagram.com")
                .header("User-Agent", "Mozilla/5.0 (iPhone)"))
        .andExpect(status().isFound());

    assertThat(clickRepository.countByLinkId(link.getId())).isEqualTo(1);
  }

  @Test
  void returns404ForUnknownCode() throws Exception {
    mvc.perform(get("/zzzzzzz"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.detail").value("link not found: zzzzzzz"));
  }

  @Test
  void returns404ForMalformedCode() throws Exception {
    mvc.perform(get("/too-short"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void returnsOgPreviewHtmlForKakaoTalkCrawler() throws Exception {
    LinkEntity link = repository.save(new LinkEntity("https://example.com/article", "kakopvw"));
    link.applyOgMetadata(
        "Article title", "Article description", "https://example.com/img.png", Instant.now());
    repository.save(link);

    mvc.perform(get("/kakopvw").header("User-Agent", "kakaotalk-scrap/1.0"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/html;charset=utf-8"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("og:title")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Article title")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("og:image")));

    // Preview hits now persist as bot click_event rows so per-link stats can split \"social
    // preview\" out of generic bot traffic. They must NOT count toward human clicks.
    assertThat(clickRepository.countByLinkId(link.getId())).isEqualTo(1);
    assertThat(clickRepository.countHumanByLinkId(link.getId())).isZero();
    assertThat(clickRepository.countBotByLinkId(link.getId())).isEqualTo(1);
  }

  @Test
  void returnsOgPreviewHtmlForSlackbot() throws Exception {
    repository.save(new LinkEntity("https://example.com/x", "slkpvw1"));

    mvc.perform(
            get("/slkpvw1")
                .header("User-Agent", "Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("twitter:card")));
  }

  @Test
  void returns410ForExpiredLink() throws Exception {
    repository.save(
        new LinkEntity(
            "https://example.com/expired",
            "exp1234",
            null,
            Instant.now().minus(1, ChronoUnit.MINUTES)));

    mvc.perform(get("/exp1234"))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
  }
}
