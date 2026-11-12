package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.link.application.dto.RecordedClick;
import com.example.short_link.link.stats.application.ClickWebhookOutbox;
import com.example.short_link.link.webhook.domain.LinkWebhookDeliveryEntity;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookDeliveryRepository;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
class ClickWebhookQueue implements ClickWebhookOutbox {

  private final LinkWebhookRepository hooks;
  private final LinkWebhookDeliveryRepository deliveries;
  private final WebhookDeliveryGate gate;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  @Override
  public void enqueue(List<RecordedClick> clicks) {
    if (clicks.isEmpty()) return;
    Set<Long> linkIds = clicks.stream().map(c -> c.linkId().value()).collect(Collectors.toSet());
    Map<Long, List<LinkWebhookEntity>> hooksByLink =
        hooks.findAllByLinkIdInAndEnabledTrue(linkIds).stream()
            .collect(Collectors.groupingBy(LinkWebhookEntity::getLinkId));
    if (hooksByLink.isEmpty()) return;
    Instant now = clock.instant();
    List<LinkWebhookDeliveryEntity> queued = new ArrayList<>();
    for (RecordedClick click : clicks) {
      for (LinkWebhookEntity hook : hooksByLink.getOrDefault(click.linkId().value(), List.of())) {
        if (passes(hook, click)) {
          queued.add(
              new LinkWebhookDeliveryEntity(
                  hook.getId(),
                  click.clickId(),
                  jsonMapper.writeValueAsString(payload(click)),
                  now));
        }
      }
    }
    if (!queued.isEmpty()) deliveries.saveAll(queued);
  }

  private boolean passes(LinkWebhookEntity hook, RecordedClick click) {
    try {
      return gate.shouldDeliver(hook, click);
    } catch (RuntimeException e) {
      log.warn("webhook: click for hookId={} skipped: {}", hook.getId(), e.toString());
      return false;
    }
  }

  static Map<String, Object> payload(RecordedClick click) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "click");
    payload.put("clickId", click.clickId());
    payload.put("linkId", click.linkId());
    payload.put("occurredAt", click.occurredAt().toString());
    payload.put("countryCode", click.countryCode() == null ? "" : click.countryCode());
    payload.put("deviceClass", click.deviceClass() == null ? "" : click.deviceClass());
    payload.put("channel", click.referrerHost() == null ? "" : click.referrerHost());
    payload.put("utmSource", click.utmSource() == null ? "" : click.utmSource());
    payload.put("bot", click.bot());
    return payload;
  }
}
