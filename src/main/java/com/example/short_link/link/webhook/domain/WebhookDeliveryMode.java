package com.example.short_link.link.webhook.domain;

/**
 * How a webhook fires:
 *
 * <ul>
 *   <li>{@link #PER_EVENT} — POST on every matching click. Combine with {@code batchEnabled} for
 *       5-second buffering of raw events.
 *   <li>{@link #DAILY_SUMMARY} — one POST per day at the hook's {@code summaryHourOfDay}, carrying
 *       yesterday's aggregate stats. Quiet by design.
 *   <li>{@link #THRESHOLD_SPIKE} — fires once when clicks within {@code spikeWindowMinutes} cross
 *       {@code spikeThreshold}. Cooldown via {@code spikeLastFiredAt} so a sustained spike doesn't
 *       spam.
 *   <li>{@link #BOTH} — DAILY_SUMMARY + THRESHOLD_SPIKE on the same hook.
 * </ul>
 */
public enum WebhookDeliveryMode {
  PER_EVENT,
  DAILY_SUMMARY,
  THRESHOLD_SPIKE,
  BOTH;

  public boolean sendsDailySummary() {
    return this == DAILY_SUMMARY || this == BOTH;
  }

  public boolean sendsSpikeAlert() {
    return this == THRESHOLD_SPIKE || this == BOTH;
  }

  public boolean sendsPerEvent() {
    return this == PER_EVENT;
  }
}
