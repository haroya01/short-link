package com.example.short_link.link.redirect.presentation;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.ClickBuffer;
import com.example.short_link.link.stats.application.ClickFlusher;
import io.queryaudit.junit5.ExpectMaxQueryCount;
import io.queryaudit.junit5.QueryAudit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@QueryAudit
class RedirectClickQueryProfileTest {

  private static final String CODE = "qprof77";

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository links;
  @Autowired private CacheManager cacheManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ClickBuffer clickBuffer;
  @Autowired private ClickFlusher clickFlusher;

  private LinkEntity link;

  @BeforeEach
  void setUp() {
    clickBuffer.drain(Integer.MAX_VALUE);
    link = links.save(new LinkEntity("https://example.com/destination", CODE));
    clear(cacheManager, "link");
  }

  @AfterEach
  void tearDown() {
    clickBuffer.drain(Integer.MAX_VALUE);
    jdbc.update("DELETE FROM click_event WHERE link_id = ?", link.getId());
    jdbc.update("DELETE FROM link WHERE id = ?", link.getId());
  }

  private int clickRows() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM click_event WHERE link_id = ?", Integer.class, link.getId());
  }

  @Test
  void firstClickOnColdCache() throws Exception {
    mvc.perform(get("/" + CODE)).andExpect(status().isFound());

    assertThat(clickRows()).isZero();
  }

  // 7 = 셋업·티어다운 6 + custom_domain 캐시 여유 1. 동기 INSERT 부활 시 클릭당 +1 로 초과.
  @Test
  @ExpectMaxQueryCount(7)
  void repeatClickOnWarmCache() throws Exception {
    mvc.perform(get("/" + CODE)).andExpect(status().isFound());
    mvc.perform(get("/" + CODE)).andExpect(status().isFound());

    assertThat(clickRows()).isZero();
  }

  @Test
  void flushMovesBufferedClicksToDbOffTheRequestThread() throws Exception {
    mvc.perform(get("/" + CODE)).andExpect(status().isFound());
    mvc.perform(get("/" + CODE)).andExpect(status().isFound());

    clickFlusher.flush();

    assertThat(clickRows()).isEqualTo(2);
    assertThat(clickBuffer.isEmpty()).isTrue();
  }
}
