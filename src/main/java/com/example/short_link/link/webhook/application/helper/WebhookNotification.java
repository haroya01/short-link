package com.example.short_link.link.webhook.application.helper;

import java.util.List;
import java.util.Map;

public sealed interface WebhookNotification {

  String eventType();

  record Click(Map<String, Object> payload) implements WebhookNotification {
    @Override
    public String eventType() {
      return "click";
    }
  }

  record Batch(long linkId, List<Map<String, Object>> events) implements WebhookNotification {
    @Override
    public String eventType() {
      return "batch";
    }
  }

  record DailySummary(DailySummaryPayload payload) implements WebhookNotification {
    @Override
    public String eventType() {
      return "daily_summary";
    }
  }

  record SpikeAlert(ThresholdSpikePayload payload) implements WebhookNotification {
    @Override
    public String eventType() {
      return "spike_alert";
    }
  }
}
