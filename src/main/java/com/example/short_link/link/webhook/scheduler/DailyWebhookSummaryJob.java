package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.common.time.ReportZones;
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
import org.springframework.transaction.support.TransactionTemplate;

// Uses the owner's timezone for the configured hour, yesterday's reporting window, and
// summaryLastSentDate daily deduplication.
@Slf4j
@Component
public class DailyWebhookSummaryJob {

  private final LinkWebhookRepository hooks;
  private final LinkRepository links;
  private final UserAccessLookup users;
  private final DailySummaryAssembler assembler;
  private final WebhookHttpDeliveryClient deliveryClient;
  private final Clock clock;
  private final TransactionTemplate transaction;

  public DailyWebhookSummaryJob(
      LinkWebhookRepository hooks,
      LinkRepository links,
      UserAccessLookup users,
      DailySummaryAssembler assembler,
      WebhookHttpDeliveryClient deliveryClient,
      Clock clock,
      TransactionTemplate transaction) {
    this.hooks = hooks;
    this.links = links;
    this.users = users;
    this.assembler = assembler;
    this.deliveryClient = deliveryClient;
    this.clock = clock;
    this.transaction = transaction;
  }

  @Scheduled(fixedDelay = 5 * 60 * 1000L)
  public void sweep() {
    List<LinkWebhookEntity> candidates =
        hooks.findAllEnabledByDeliveryModes(WebhookDeliveryMode.dailySummaryModes());
    for (LinkWebhookEntity hook : candidates) {
      try {
        tryDeliverFor(hook);
      } catch (RuntimeException failure) {
        log.warn("daily webhook summary failed: hookId={}", hook.getId(), failure);
      }
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
    ZoneId tz = ReportZones.zoneOf(ownerTimezone);
    ZonedDateTime nowLocal = ZonedDateTime.now(clock.withZone(tz));
    LocalDate today = nowLocal.toLocalDate();
    if (nowLocal.getHour() < hourOfDay) return;
    if (today.equals(hook.getSummaryLastSentDate())) return;
    LocalDate yesterday = today.minusDays(1);

    DailySummaryPayload payload =
        assembler.assemble(hook.linkId(), link.getShortCode(), yesterday, tz);
    deliveryClient.deliver(hook, new WebhookNotification.DailySummary(payload));
    transaction.executeWithoutResult(
        status -> hooks.findById(hook.getId()).ifPresent(sent -> sent.markSummarySent(today)));
  }

  Optional<LocalDate> peekLastSent(LinkWebhookEntity hook) {
    return Optional.ofNullable(hook.getSummaryLastSentDate());
  }
}
