package com.example.short_link.link.og.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OgRefreshJobTest {

  @Autowired private LinkRepository linkRepository;

  @Test
  void staleQueryReturnsOldOkRowsOnly() {
    Instant now = Instant.now();

    LinkEntity stale =
        linkRepository.save(new LinkEntity("https://example.com/stale", "rfsh001", null, null));
    stale.applyOgMetadata(
        "old title", "old desc", "https://img/old.png", now.minus(Duration.ofDays(40)));
    linkRepository.save(stale);

    LinkEntity fresh =
        linkRepository.save(new LinkEntity("https://example.com/fresh", "rfsh002", null, null));
    fresh.applyOgMetadata(
        "new title", "new desc", "https://img/new.png", now.minus(Duration.ofDays(5)));
    linkRepository.save(fresh);

    LinkEntity retryable =
        linkRepository.save(new LinkEntity("https://example.com/retry", "rfsh003", null, null));
    retryable.markOgFetchFailed(now.minus(Duration.ofDays(40)), true);
    linkRepository.save(retryable);

    Instant cutoff = now.minus(Duration.ofDays(30));
    List<LinkEntity> stale30 = linkRepository.findStaleOgCandidates(cutoff, PageRequest.of(0, 10));

    assertThat(stale30).extracting(l -> l.getShortCode().value()).containsExactly("rfsh001");
  }
}
