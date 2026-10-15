package com.example.short_link.blogimport.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.blogimport.application.BlogImportProperties.RobotsPolicy;
import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RobotsGateTest {

  private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
  private static final String NAVER_FEED = "https://rss.blog.naver.com/naverofficial.xml";

  private final BlogFeedFetcher fetcher = mock(BlogFeedFetcher.class);

  private RobotsGate gate(RobotsPolicy policy, Instant now) {
    return new RobotsGate(
        fetcher, new BlogImportProperties(policy, 0, null, null), Clock.fixed(now, ZoneOffset.UTC));
  }

  private void naverShutsEveryoneOut() {
    when(fetcher.robots("https://rss.blog.naver.com"))
        .thenReturn(Optional.of("User-agent: *\nDisallow: /\n\nUser-agent: Yeti\nDisallow: /\n"));
  }

  @Test
  void respectIsTheDefaultAndBlocksNaversFeeds() {
    naverShutsEveryoneOut();
    RobotsGate gate =
        new RobotsGate(
            fetcher,
            new BlogImportProperties(null, 0, null, null),
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> gate.requireAllowed(NAVER_FEED))
        .isInstanceOfSatisfying(
            BlogImportException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BlogImportErrorCode.IMPORT_ROBOTS_DISALLOWED));
  }

  @Test
  void namedOnlyLetsAWildcardBanPassButNotABanOnUs() {
    naverShutsEveryoneOut();
    RobotsGate gate = gate(RobotsPolicy.NAMED_ONLY, NOW);
    assertThatCode(() -> gate.requireAllowed(NAVER_FEED)).doesNotThrowAnyException();

    when(fetcher.robots("https://blocks.tistory.com"))
        .thenReturn(Optional.of("User-agent: kurl-importer\nDisallow: /rss\n"));
    assertThatThrownBy(() -> gate.requireAllowed("https://blocks.tistory.com/rss"))
        .isInstanceOf(BlogImportException.class);
  }

  @Test
  void ignoreNeverReadsRobots() {
    gate(RobotsPolicy.IGNORE, NOW).requireAllowed(NAVER_FEED);

    verifyNoInteractions(fetcher);
  }

  @Test
  void aSiteWithoutRobotsIsOpenAndEverySyncReadsRobotsAgain() {
    when(fetcher.robots("https://notice.tistory.com")).thenReturn(Optional.empty());
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(60), NOW.plus(RobotsGate.CACHE_FOR), NOW);
    RobotsGate gate =
        new RobotsGate(
            fetcher, new BlogImportProperties(RobotsPolicy.RESPECT, 0, null, null), clock);

    gate.requireAllowed("https://notice.tistory.com/rss");
    gate.requireAllowed("https://notice.tistory.com/rss?page=2");
    verify(fetcher, times(1)).robots(anyString());

    gate.requireAllowed("https://notice.tistory.com/rss");
    verify(fetcher, times(2)).robots(anyString());
    assertThat(RobotsGate.CACHE_FOR).isLessThan(ImportSourceEntity.FETCH_INTERVAL);
  }

  @Test
  void anUnreadableRobotsTxtIsNotCached() {
    when(fetcher.robots("https://down.tistory.com"))
        .thenThrow(new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, "x"))
        .thenReturn(Optional.empty());
    RobotsGate gate = gate(RobotsPolicy.RESPECT, NOW);

    assertThatThrownBy(() -> gate.requireAllowed("https://down.tistory.com/rss"))
        .isInstanceOf(BlogImportException.class);
    assertThatCode(() -> gate.requireAllowed("https://down.tistory.com/rss"))
        .doesNotThrowAnyException();
  }
}
