package com.example.short_link.link.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        .andExpect(header().string("Cache-Control", "private, max-age=90"));
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
