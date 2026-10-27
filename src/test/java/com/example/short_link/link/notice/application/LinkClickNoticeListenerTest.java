package com.example.short_link.link.notice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.common.notification.LinkNotice;
import com.example.short_link.common.notification.LinkNoticeBody;
import com.example.short_link.common.notification.LinkNoticeKind;
import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickRangeReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkClickNoticeListenerTest {

  private static final Instant AT = Instant.parse("2026-10-27T03:00:00Z");

  private final LinkRepository links = mock(LinkRepository.class);
  private final ClickTotalsReadRepository totals = mock(ClickTotalsReadRepository.class);
  private final ClickRangeReadRepository ranges = mock(ClickRangeReadRepository.class);
  private final RedisDistributedLock cooldown = mock(RedisDistributedLock.class);
  private final List<LinkNotice> sent = new ArrayList<>();

  private final LinkClickNoticeListener listener =
      new LinkClickNoticeListener(
          links,
          totals,
          ranges,
          sent::add,
          new LinkNoticeProperties(true, true, 7, 10, 3.0),
          cooldown);

  private static ClickRecordedEvent event(boolean bot) {
    return new ClickRecordedEvent(
        LinkId.of(1L), "abc1234", 5L, AT, "KR", "mobile", "direct", bot, null);
  }

  private static LinkEntity link(String note) {
    LinkEntity link = new LinkEntity("https://blog.example.com/spring", "spring", 5L, null);
    link.updateNote(note);
    return link;
  }

  private static LinkNotice notice(LinkNoticeKind kind, String label, String text) {
    return new LinkNotice(5L, kind, "spring", label, new LinkNoticeBody.Written(text));
  }

  private void clicks(long human) {
    when(links.findById(1L)).thenReturn(Optional.of(link(null)));
    when(totals.countHumanByLinkId(1L)).thenReturn(human);
  }

  @Test
  void aBotClickReadsNothingAndTellsNoOne() {
    listener.onClickRecorded(event(true));

    assertThat(sent).isEmpty();
    verify(links, never()).findById(anyLong());
  }

  @Test
  void aLinkWithoutAnOwnerTellsNoOne() {
    when(links.findById(1L))
        .thenReturn(Optional.of(new LinkEntity("https://x.example", "spring", null, null)));

    listener.onClickRecorded(event(false));

    assertThat(sent).isEmpty();
    verify(totals, never()).countHumanByLinkId(anyLong());
  }

  @Test
  void theFirstHumanClickIsTheOnlyNoticeItGets() {
    clicks(1L);

    listener.onClickRecorded(event(false));

    assertThat(sent)
        .containsExactly(notice(LinkNoticeKind.FIRST_CLICK, "/spring", "첫 클릭이 들어왔어요 🎉"));
    verify(ranges, never()).countHumanByLinkIdAndRange(anyLong(), any(), any());
  }

  @Test
  void theNoteNamesTheLinkWhenItHasOne() {
    when(links.findById(1L)).thenReturn(Optional.of(link("봄 세일")));
    when(totals.countHumanByLinkId(1L)).thenReturn(1L);

    listener.onClickRecorded(event(false));

    assertThat(sent).extracting(LinkNotice::label).containsExactly("봄 세일");
  }

  @Test
  void aMilestoneBelowTheSpikeFloorReadsNoRanges() {
    when(links.findById(1L)).thenReturn(Optional.of(link(null)));
    when(totals.countHumanByLinkId(1L)).thenReturn(10L);
    LinkClickNoticeListener strict =
        new LinkClickNoticeListener(
            links,
            totals,
            ranges,
            sent::add,
            new LinkNoticeProperties(true, true, 7, 11, 3.0),
            cooldown);

    strict.onClickRecorded(event(false));

    assertThat(sent).containsExactly(notice(LinkNoticeKind.MILESTONE, "/spring", "10 클릭을 넘었어요"));
    verify(ranges, never()).countHumanByLinkIdAndRange(anyLong(), any(), any());
  }

  @Test
  void aSpikeComparesTheLastHourWithTheDayBeforeIt() {
    clicks(50L);
    when(ranges.countHumanByLinkIdAndRange(1L, AT.minus(Duration.ofHours(1)), AT)).thenReturn(20L);
    when(ranges.countHumanByLinkIdAndRange(
            1L, AT.minus(Duration.ofHours(25)), AT.minus(Duration.ofHours(1))))
        .thenReturn(24L);
    when(cooldown.tryAcquire("notif:velocity:1", Duration.ofHours(12))).thenReturn(true);

    listener.onClickRecorded(event(false));

    assertThat(sent)
        .containsExactly(
            notice(LinkNoticeKind.VELOCITY_SPIKE, "/spring", "지금 평소의 20배 — 어디서 퍼지는지 보세요"));
  }

  @Test
  void aMilestoneAndASpikeCanArriveTogether() {
    clicks(100L);
    when(ranges.countHumanByLinkIdAndRange(eq(1L), any(), any())).thenReturn(30L, 0L);
    when(cooldown.tryAcquire("notif:velocity:1", Duration.ofHours(12))).thenReturn(true);

    listener.onClickRecorded(event(false));

    assertThat(sent)
        .extracting(LinkNotice::kind)
        .containsExactly(LinkNoticeKind.MILESTONE, LinkNoticeKind.VELOCITY_SPIKE);
  }

  @Test
  void aQuietLastHourSkipsTheDayBeforeIt() {
    clicks(50L);
    when(ranges.countHumanByLinkIdAndRange(eq(1L), any(), any())).thenReturn(2L);

    listener.onClickRecorded(event(false));

    assertThat(sent).isEmpty();
    verify(ranges).countHumanByLinkIdAndRange(eq(1L), eq(AT.minus(Duration.ofHours(1))), eq(AT));
    verify(ranges, never())
        .countHumanByLinkIdAndRange(eq(1L), eq(AT.minus(Duration.ofHours(25))), any());
  }

  @Test
  void aSpikeWithinTheCooldownIsNotRepeated() {
    clicks(50L);
    when(ranges.countHumanByLinkIdAndRange(eq(1L), any(), any())).thenReturn(20L, 0L);
    when(cooldown.tryAcquire("notif:velocity:1", Duration.ofHours(12))).thenReturn(false);

    listener.onClickRecorded(event(false));

    assertThat(sent).isEmpty();
  }

  @Test
  void aBelowThresholdSpikeTakesNoCooldown() {
    clicks(50L);
    when(ranges.countHumanByLinkIdAndRange(eq(1L), any(), any())).thenReturn(3L, 48L);

    listener.onClickRecorded(event(false));

    assertThat(sent).isEmpty();
    verify(cooldown, never()).tryAcquire(any(), any());
  }

  @Test
  void aFailingReadIsSwallowedAndTellsNoOne() {
    when(links.findById(1L)).thenThrow(new IllegalStateException("db down"));

    listener.onClickRecorded(event(false));

    assertThat(sent).isEmpty();
  }
}
