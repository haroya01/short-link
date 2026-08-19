package com.example.short_link.link.redirect.presentation;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.admin.application.read.BlockedDomainCache;
import com.example.short_link.admin.application.write.BlockDomainUseCase;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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

/** 생성 후 차단된 도메인 — 기존 링크의 302·크롤러 프리뷰가 모두 차단 페이지로 죽는지. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RedirectControllerDomainBlockTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository repository;
  @Autowired private UserRepository userRepository;
  @Autowired private BlockDomainUseCase blockDomain;
  @Autowired private CacheManager cacheManager;
  @Autowired private LinkDestinationRepository destinationRepository;

  @BeforeEach
  void clearCaches() {
    clear(cacheManager, "link");
    clear(cacheManager, BlockedDomainCache.CACHE_NAME);
  }

  private void blockDomainNow(String domain) {
    UserEntity admin = userRepository.save(new UserEntity("adm@x.com", "google", "g-dbk"));
    blockDomain.execute(domain, "spam", admin.getId());
    // 롤백 트랜잭션에선 afterCommit 이벤트가 안 와서 캐시를 직접 비운다.
    clear(cacheManager, BlockedDomainCache.CACHE_NAME);
  }

  @Test
  void existingLinkToBlockedDomainRendersDisabledPageInsteadOfRedirect() throws Exception {
    repository.save(new LinkEntity("https://spam.example.com/promo", "dbk1234"));
    blockDomainNow("spam.example.com");

    mvc.perform(get("/dbk1234"))
        .andExpect(status().isForbidden())
        .andExpect(content().string(Matchers.containsString("차단된 링크")));
  }

  @Test
  void subdomainOfBlockedDomainIsAlsoBlocked() throws Exception {
    repository.save(new LinkEntity("https://go.spam.example.com/x", "dbk2345"));
    blockDomainNow("spam.example.com");

    mvc.perform(get("/dbk2345")).andExpect(status().isForbidden());
  }

  @Test
  void crawlerGetsDisabledPageNotOgPreview() throws Exception {
    repository.save(new LinkEntity("https://spam.example.com/promo", "dbk3456"));
    blockDomainNow("spam.example.com");

    mvc.perform(get("/dbk3456").header("User-Agent", "facebookexternalhit/1.1"))
        .andExpect(status().isForbidden())
        .andExpect(content().string(Matchers.containsString("차단된 링크")));
  }

  /**
   * originalUrl 은 깨끗한데 목적지 변형(variant)만 차단 도메인인 링크 — 크롤러도 OG 카드를 못 받는다. 예전엔 크롤러 경로가 originalUrl 만
   * 검사해 legitimate 카드가 나갔다(#659 의도 무력화).
   */
  @Test
  void crawlerGetsDisabledPageWhenOnlyVariantDestinationIsBlocked() throws Exception {
    LinkEntity link = repository.save(new LinkEntity("https://clean.example.com/ok", "dbk7890"));
    LinkEntity stored = repository.findByShortCode(new ShortCode("dbk7890")).orElseThrow();
    destinationRepository.save(
        new LinkDestinationEntity(
            new LinkId(stored.getId()), "https://spam.example.com/promo", 1, "geo", "KR"));
    blockDomainNow("spam.example.com");

    mvc.perform(get("/dbk7890").header("User-Agent", "Discordbot/2.0"))
        .andExpect(status().isForbidden())
        .andExpect(content().string(Matchers.containsString("차단된 링크")));
  }

  @Test
  void unrelatedDomainStillRedirects() throws Exception {
    repository.save(new LinkEntity("https://clean.example.com/ok", "dbk4567"));
    blockDomainNow("spam.example.com");

    mvc.perform(get("/dbk4567"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://clean.example.com/ok"));
  }
}
