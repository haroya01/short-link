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

/**
 * 리다이렉트 한 번이 요청 스레드에서 내는 쿼리의 전량 프로파일. 트랜잭션 롤백 없이 실제 커밋 경로를 그대로 태워 프로드 클릭과 동일한 쿼리 시퀀스를 query-audit
 * 리포트로 남긴다.
 *
 * <p>비동기 전환 후 계약: 요청 스레드는 click_event INSERT 를 내지 않는다(302 는 클릭 기록을 기다리지 않는다). INSERT 는 플러시 프로파일
 * 테스트에서만 나타나야 한다.
 */
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
    // 다른 테스트가 흘린 대기 클릭이 이 프로파일에 섞이지 않게 버퍼를 비우고 시작한다.
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

  // 예산 7 = 셋업·티어다운 6 + custom_domain 캐시 상태 여유 1. 동기 INSERT 가 부활하면 클릭당 +1 이라 초과된다.
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
