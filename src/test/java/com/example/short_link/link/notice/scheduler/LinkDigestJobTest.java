package com.example.short_link.link.notice.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.common.notification.LinkNotice;
import com.example.short_link.common.notification.LinkNoticeBody;
import com.example.short_link.common.notification.LinkNoticeKind;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.notice.application.LinkNoticeProperties;
import com.example.short_link.link.stats.application.read.ClickRangeReader;
import com.example.short_link.link.stats.domain.projection.ClickProjections.LinkClickCount;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkDigestJobTest {

  private final LinkRepository links = mock(LinkRepository.class);
  private final ClickRangeReader ranges = mock(ClickRangeReader.class);
  private final RedisDistributedLock lock = mock(RedisDistributedLock.class);
  private final List<LinkNotice> sent = new ArrayList<>();

  private LinkDigestJob job(boolean enabled) {
    return new LinkDigestJob(
        links, ranges, sent::add, new LinkNoticeProperties(enabled, true, 7, 10, 3.0), lock);
  }

  private static LinkClickCount top(long linkId, long count) {
    return new LinkClickCount() {
      @Override
      public Long getLinkId() {
        return linkId;
      }

      @Override
      public Long getCount() {
        return count;
      }
    };
  }

  @Test
  void ownersWithClicksYesterdayHearTheirTotalAndTopLink() {
    when(lock.tryAcquire("notif:digest", Duration.ofMinutes(10))).thenReturn(true);
    when(links.findDistinctUserIds()).thenReturn(Arrays.asList(5L, null, 6L, 7L));
    when(ranges.countHumanByUserIdAndRange(any(), any(), any())).thenReturn(12L, 0L, 3L);
    LinkEntity noted = new LinkEntity("https://x.example", "spring", 5L, null);
    noted.updateNote("봄 세일");
    when(ranges.findTopLinksByUserIdAndRange(any(), any(), any(), eq(1)))
        .thenReturn(List.of(top(1L, 9L)), List.of());
    when(links.findById(1L)).thenReturn(Optional.of(noted));

    job(true).tick();

    assertThat(sent)
        .containsExactly(
            new LinkNotice(
                5L,
                LinkNoticeKind.DIGEST,
                null,
                "어제 요약",
                new LinkNoticeBody.Written("어제 클릭 12회 · 톱 봄 세일")),
            new LinkNotice(
                7L, LinkNoticeKind.DIGEST, null, "어제 요약", new LinkNoticeBody.Written("어제 클릭 3회")));
    verify(lock).release("notif:digest");
  }

  @Test
  void aDisabledDigestTakesNoLock() {
    job(false).tick();

    assertThat(sent).isEmpty();
    verify(lock, never()).tryAcquire(anyString(), any());
  }

  @Test
  void anotherInstanceHoldingTheLockSendsNothingHere() {
    when(lock.tryAcquire("notif:digest", Duration.ofMinutes(10))).thenReturn(false);

    job(true).tick();

    assertThat(sent).isEmpty();
    verify(links, never()).findDistinctUserIds();
  }
}
