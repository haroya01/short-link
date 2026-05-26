package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.webhook.application.helper.DailySummaryAssembler;
import com.example.short_link.link.webhook.application.helper.DailySummaryPayload;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DailyWebhookSummaryJobTest {

  private final LinkWebhookRepository hooks = mock(LinkWebhookRepository.class);
  private final LinkRepository links = mock(LinkRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final DailySummaryAssembler assembler = mock(DailySummaryAssembler.class);
  private final LinkWebhookDispatcher dispatcher = mock(LinkWebhookDispatcher.class);
  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private LinkWebhookEntity hookWithSummary(int hour) {
    LinkWebhookEntity hook = new LinkWebhookEntity(1L, "https://example.com/h", "secret", "n");
    hook.changeDeliveryMode(WebhookDeliveryMode.DAILY_SUMMARY, hour, null, null);
    return hook;
  }

  private LinkEntity link() {
    LinkEntity l = new LinkEntity("https://target", "abc", 7L, null);
    setId(l, 1L);
    return l;
  }

  private static void setId(LinkEntity link, long id) {
    try {
      var f = LinkEntity.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(link, id);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private UserEntity userInSeoul() {
    UserEntity u = mock(UserEntity.class);
    when(u.getTimezone()).thenReturn("Asia/Seoul");
    return u;
  }

  private Clock clockAt(ZonedDateTime t) {
    return Clock.fixed(t.toInstant(), t.getZone());
  }

  private DailyWebhookSummaryJob jobAt(ZonedDateTime t) {
    return new DailyWebhookSummaryJob(
        hooks, links, users, assembler, dispatcher, jsonMapper, clockAt(t));
  }

  private DailySummaryPayload stubPayload() {
    return new DailySummaryPayload(
        new ShortCode("abc"), "a", "b", 0, 0, 0, 0, null, null, null, 0, 0, null, null);
  }

  @Test
  void firesWhenHourReachedAndNotSentToday() {
    LinkWebhookEntity hook = hookWithSummary(9);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    UserEntity owner = userInSeoul();
    when(users.findById(7L)).thenReturn(Optional.of(owner));
    when(assembler.assemble(
            eq(1L), eq(new ShortCode("abc")), any(LocalDate.class), any(ZoneId.class)))
        .thenReturn(stubPayload());

    jobAt(ZonedDateTime.of(2026, 5, 25, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"))).sweep();

    verify(dispatcher, times(1)).deliver(eq(hook), anyString(), eq("daily_summary"));
  }

  @Test
  void marksSummarySentAfterDelivery() {
    LinkWebhookEntity hook = hookWithSummary(9);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    UserEntity owner = userInSeoul();
    when(users.findById(7L)).thenReturn(Optional.of(owner));
    when(assembler.assemble(
            anyLong(), any(ShortCode.class), any(LocalDate.class), any(ZoneId.class)))
        .thenReturn(stubPayload());

    jobAt(ZonedDateTime.of(2026, 5, 25, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"))).sweep();

    assertThat(hook.getSummaryLastSentDate()).isEqualTo(LocalDate.of(2026, 5, 25));
  }

  @Test
  void doesNotFireBeforeConfiguredHour() {
    LinkWebhookEntity hook = hookWithSummary(9);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    UserEntity owner = userInSeoul();
    when(users.findById(7L)).thenReturn(Optional.of(owner));

    jobAt(ZonedDateTime.of(2026, 5, 25, 8, 59, 0, 0, ZoneId.of("Asia/Seoul"))).sweep();

    verify(dispatcher, never()).deliver(any(), anyString(), anyString());
  }

  @Test
  void doesNotFireTwiceSameLocalDay() {
    LinkWebhookEntity hook = hookWithSummary(9);
    hook.markSummarySent(LocalDate.of(2026, 5, 25));
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    UserEntity owner = userInSeoul();
    when(users.findById(7L)).thenReturn(Optional.of(owner));

    jobAt(ZonedDateTime.of(2026, 5, 25, 14, 0, 0, 0, ZoneId.of("Asia/Seoul"))).sweep();

    verify(dispatcher, never()).deliver(any(), anyString(), anyString());
  }

  @Test
  void firesAgainNextDay() {
    LinkWebhookEntity hook = hookWithSummary(9);
    hook.markSummarySent(LocalDate.of(2026, 5, 24));
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    UserEntity owner = userInSeoul();
    when(users.findById(7L)).thenReturn(Optional.of(owner));
    when(assembler.assemble(
            anyLong(), any(ShortCode.class), any(LocalDate.class), any(ZoneId.class)))
        .thenReturn(stubPayload());

    jobAt(ZonedDateTime.of(2026, 5, 25, 9, 5, 0, 0, ZoneId.of("Asia/Seoul"))).sweep();

    verify(dispatcher, times(1)).deliver(eq(hook), anyString(), eq("daily_summary"));
  }

  @Test
  void skipsHookWhenOwnerMissing() {
    LinkWebhookEntity hook = hookWithSummary(9);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    when(users.findById(7L)).thenReturn(Optional.empty());

    jobAt(ZonedDateTime.of(2026, 5, 25, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"))).sweep();

    verify(dispatcher, never()).deliver(any(), anyString(), anyString());
  }

  @Test
  void skipsHookWhenLinkMissing() {
    LinkWebhookEntity hook = hookWithSummary(9);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.empty());

    jobAt(ZonedDateTime.of(2026, 5, 25, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"))).sweep();

    verify(dispatcher, never()).deliver(any(), anyString(), anyString());
  }

  @Test
  void usesYesterdayAsWindow() {
    LinkWebhookEntity hook = hookWithSummary(9);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    UserEntity owner = userInSeoul();
    when(users.findById(7L)).thenReturn(Optional.of(owner));
    when(assembler.assemble(
            eq(1L), eq(new ShortCode("abc")), eq(LocalDate.of(2026, 5, 24)), any(ZoneId.class)))
        .thenReturn(stubPayload());

    jobAt(ZonedDateTime.of(2026, 5, 25, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"))).sweep();

    verify(assembler, times(1))
        .assemble(
            eq(1L), eq(new ShortCode("abc")), eq(LocalDate.of(2026, 5, 24)), any(ZoneId.class));
  }

  @Test
  void falsyTimezoneFallsBackToSeoul() {
    LinkWebhookEntity hook = hookWithSummary(9);
    UserEntity owner = mock(UserEntity.class);
    when(owner.getTimezone()).thenReturn("Not/A/Real/Zone");
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    when(users.findById(7L)).thenReturn(Optional.of(owner));
    when(assembler.assemble(
            anyLong(), any(ShortCode.class), any(LocalDate.class), any(ZoneId.class)))
        .thenReturn(stubPayload());

    jobAt(ZonedDateTime.of(2026, 5, 25, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"))).sweep();

    verify(assembler, times(1))
        .assemble(
            eq(1L), eq(new ShortCode("abc")), any(LocalDate.class), eq(ZoneId.of("Asia/Seoul")));
  }

  @Test
  void emptyCandidateListIsNoOp() {
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of());

    jobAt(ZonedDateTime.of(2026, 5, 25, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"))).sweep();

    verify(dispatcher, never()).deliver(any(), anyString(), anyString());
  }
}
