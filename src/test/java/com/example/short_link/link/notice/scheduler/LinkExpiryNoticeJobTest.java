package com.example.short_link.link.notice.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.common.notification.LinkNotice;
import com.example.short_link.common.notification.LinkNoticeBody;
import com.example.short_link.common.notification.LinkNoticeKind;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.notice.application.LinkNoticeProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LinkExpiryNoticeJobTest {

  private final LinkRepository links = mock(LinkRepository.class);
  private final RedisDistributedLock lock = mock(RedisDistributedLock.class);
  private final List<LinkNotice> sent = new ArrayList<>();

  @Test
  void ownedLinksInTheDailyBandHearTheyExpireSoon() {
    when(lock.tryAcquire("notif:expiry", Duration.ofMinutes(10))).thenReturn(true);
    LinkEntity noted = new LinkEntity("https://x.example", "spring", 5L, null);
    noted.updateNote("봄 세일");
    when(links.findByExpiresAtBetween(any(), any()))
        .thenReturn(
            List.of(
                noted,
                new LinkEntity("https://y.example", "autumn", null, null),
                new LinkEntity("https://z.example", "winter", 6L, null)));

    new LinkExpiryNoticeJob(
            links, sent::add, new LinkNoticeProperties(true, true, 3, 10, 3.0), lock)
        .tick();

    assertThat(sent)
        .containsExactly(
            new LinkNotice(
                5L,
                LinkNoticeKind.EXPIRY_IMMINENT,
                "spring",
                "봄 세일",
                new LinkNoticeBody.Written("약 3일 뒤 만료돼요")),
            new LinkNotice(
                6L,
                LinkNoticeKind.EXPIRY_IMMINENT,
                "winter",
                "/winter",
                new LinkNoticeBody.Written("약 3일 뒤 만료돼요")));
    ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
    verify(links).findByExpiresAtBetween(from.capture(), to.capture());
    assertThat(Duration.between(from.getValue(), to.getValue())).isEqualTo(Duration.ofDays(1));
    verify(lock).release("notif:expiry");
  }
}
