package com.example.short_link.link.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExpiredLinkCleanupJobTest {

  @Autowired private ExpiredLinkCleanupJob job;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickEventRepository;

  @Test
  void deletesLinksPastGracePeriod() {
    Instant longAgo = Instant.now().minus(Duration.ofDays(60));

    LinkEntity expiredLink = new LinkEntity("https://example.com/old", "expired1", null, longAgo);
    expiredLink = linkRepository.save(expiredLink);
    clickEventRepository.save(
        ClickEventEntity.builder().linkId(expiredLink.getId()).bot(false).build());

    LinkEntity recentExpiredLink =
        linkRepository.save(
            new LinkEntity(
                "https://example.com/recent",
                "recent01",
                null,
                Instant.now().minus(Duration.ofDays(5))));

    LinkEntity activeLink =
        linkRepository.save(new LinkEntity("https://example.com/active", "active01", null, null));

    int deleted = job.sweep();

    assertThat(deleted).isEqualTo(1);
    assertThat(linkRepository.findByShortCode("expired1")).isEmpty();
    assertThat(linkRepository.findByShortCode("recent01")).isPresent();
    assertThat(linkRepository.findByShortCode("active01")).isPresent();
    assertThat(clickEventRepository.countByLinkId(expiredLink.getId())).isZero();
  }

  @Test
  void doesNothingWhenNothingToCleanup() {
    int deleted = job.sweep();
    assertThat(deleted).isZero();
  }

  // Reproduces the @Scheduled context (no ambient transaction). If runDaily() doesn't open its
  // own transaction, the self-invocation of sweep() bypasses the @Transactional proxy and the
  // batch loop's queries run outside of any managed transaction.
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void runDailyOpensOwnTransactionForSweep() {
    Instant longAgo = Instant.now().minus(Duration.ofDays(90));
    LinkEntity old =
        linkRepository.save(new LinkEntity("https://example.com/probe", "prob0001", null, longAgo));
    Long oldId = old.getId();
    try {
      job.runDaily();
      assertThat(linkRepository.findById(oldId)).isEmpty();
    } finally {
      linkRepository.findById(oldId).ifPresent(linkRepository::delete);
    }
  }
}
