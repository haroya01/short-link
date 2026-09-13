package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.webhook.application.helper.DailySummaryAssembler;
import com.example.short_link.link.webhook.application.helper.DailySummaryPayload;
import com.example.short_link.link.webhook.application.helper.WebhookNotification;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uses the owner's timezone for the configured hour, yesterday's reporting window, and {@code
 * summaryLastSentDate} daily deduplication.
 */
@Slf4j
@Component
public class DailyWebhookSummaryJob {

  private final LinkWebhookRepository hooks;
  private final LinkRepository links;
  private final UserAccessLookup users;
  private final DailySummaryAssembler assembler;
  private final WebhookHttpDeliveryClient deliveryClient;
  private final Clock clock;

  public DailyWebhookSummaryJob(
      LinkWebhookRepository hooks,
      LinkRepository links,
      UserAccessLookup users,
      DailySummaryAssembler assembler,
      WebhookHttpDeliveryClient deliveryClient,
      Clock clock) {
    this.hooks = hooks;
    this.links = links;
    this.users = users;
    this.assembler = assembler;
    this.deliveryClient = deliveryClient;
    this.clock = clock;
  }

  @Scheduled(fixedDelay = 5 * 60 * 1000L)
  @Transactional
  public void sweep() {
    List<LinkWebhookEntity> candidates =
        hooks.findAllEnabledByDeliveryModes(WebhookDeliveryMode.dailySummaryModes());
    if (candidates.isEmpty()) return;
    for (LinkWebhookEntity hook : candidates) {
      tryDeliverFor(hook);
    }
  }

  void tryDeliverFor(LinkWebhookEntity hook) {
    Integer hourOfDay = hook.getSummaryHourOfDay();
    if (hourOfDay == null) return;
    LinkEntity link = links.findById(hook.getLinkId()).orElse(null);
    if (link == null) return;
    if (link.getUserId() == null) return;
    String ownerTimezone = users.timezone(link.getUserId()).orElse(null);
    if (ownerTimezone == null) return;
    ZoneId tz = resolveZone(ownerTimezone);
    ZonedDateTime nowLocal = ZonedDateTime.now(clock.withZone(tz));
    LocalDate today = nowLocal.toLocalDate();
    if (nowLocal.getHour() < hourOfDay) return;
    if (today.equals(hook.getSummaryLastSentDate())) return;
    LocalDate yesterday = today.minusDays(1);

    DailySummaryPayload payload =
        assembler.assemble(hook.linkId(), link.getShortCode(), yesterday, tz);
    deliveryClient.deliver(hook, new WebhookNotification.DailySummary(payload));
    hook.markSummarySent(today);
  }

  private static ZoneId resolveZone(String name) {
    if (name == null || name.isBlank()) return ZoneId.of("Asia/Seoul");
    try {
      return ZoneId.of(name);
    } catch (Exception e) {
      return ZoneId.of("Asia/Seoul");
    }
  }

  Optional<LocalDate> peekLastSent(LinkWebhookEntity hook) {
    return Optional.ofNullable(hook.getSummaryLastSentDate());
  }
}
