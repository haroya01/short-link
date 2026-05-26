package com.example.short_link.link.webhook.application.helper;

import com.example.short_link.link.webhook.domain.WebhookFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the receiver-specific request body from a generic kurl click payload (or batch). Both
 * managed receivers reject anything that doesn't fit their shape:
 *
 * <ul>
 *   <li>Discord requires {@code content} and/or {@code embeds} — a bare kurl JSON returns 400 with
 *       "Cannot send an empty message" and trips our auto-disable after 5 in a row.
 *   <li>Slack requires {@code text} or {@code blocks} — same failure mode.
 * </ul>
 *
 * Kept stateless and free of Spring wiring so it can be unit-tested without context bootstrap.
 */
public final class WebhookPayloadAdapter {

  private WebhookPayloadAdapter() {}

  /** Discord embed accent color — picked to match the kurl brand green (#059669). */
  private static final int DISCORD_BRAND_COLOR = 0x059669;

  /** Slack/Discord display name on incoming messages. */
  private static final String SENDER_NAME = "kurl";

  public static Map<String, Object> buildClick(WebhookFormat format, Map<String, Object> click) {
    return switch (format) {
      case GENERIC -> click;
      case DISCORD -> discordClick(click);
      case SLACK -> slackClick(click);
    };
  }

  public static Map<String, Object> buildBatch(
      WebhookFormat format, long linkId, List<Map<String, Object>> events) {
    return switch (format) {
      case GENERIC ->
          Map.of("type", "click.batch", "linkId", linkId, "count", events.size(), "events", events);
      case DISCORD -> discordBatch(linkId, events);
      case SLACK -> slackBatch(linkId, events);
    };
  }

  private static Map<String, Object> discordClick(Map<String, Object> click) {
    String description = clickSummaryLine(click);
    Map<String, Object> embed = new LinkedHashMap<>();
    embed.put("title", "kurl click: link #" + str(click.get("linkId")));
    embed.put("description", description);
    embed.put("color", DISCORD_BRAND_COLOR);
    embed.put("timestamp", isoTimestamp(click.get("occurredAt")));
    embed.put("fields", clickFields(click));
    return Map.of("username", SENDER_NAME, "embeds", List.of(embed));
  }

  private static Map<String, Object> discordBatch(long linkId, List<Map<String, Object>> events) {
    Map<String, Object> embed = new LinkedHashMap<>();
    embed.put("title", "kurl batch: " + events.size() + " click(s) for link #" + linkId);
    embed.put(
        "description",
        events.isEmpty()
            ? "(no events)"
            : "Most recent: " + clickSummaryLine(events.get(events.size() - 1)));
    embed.put("color", DISCORD_BRAND_COLOR);
    return Map.of(
        "username",
        SENDER_NAME,
        "content",
        "kurl click batch (" + events.size() + ")",
        "embeds",
        List.of(embed));
  }

  private static List<Map<String, Object>> clickFields(Map<String, Object> click) {
    List<Map<String, Object>> fields = new ArrayList<>();
    addField(fields, "Country", str(click.get("countryCode")));
    addField(fields, "Device", str(click.get("deviceClass")));
    addField(fields, "Channel", str(click.get("channel")));
    addField(fields, "UTM Source", str(click.get("utmSource")));
    Object bot = click.get("bot");
    if (Boolean.TRUE.equals(bot)) {
      addField(fields, "Bot", "yes");
    }
    return fields;
  }

  private static void addField(List<Map<String, Object>> fields, String name, String value) {
    if (value == null || value.isBlank()) return;
    fields.add(Map.of("name", name, "value", value, "inline", true));
  }

  private static Map<String, Object> slackClick(Map<String, Object> click) {
    String summary =
        "kurl click: link #" + str(click.get("linkId")) + " — " + clickSummaryLine(click);
    return Map.of(
        "username", SENDER_NAME,
        "text", summary,
        "blocks", slackBlocks(click));
  }

  private static List<Map<String, Object>> slackBlocks(Map<String, Object> click) {
    String header = "kurl click on link #" + str(click.get("linkId"));
    Map<String, Object> headerBlock =
        Map.of("type", "header", "text", Map.of("type", "plain_text", "text", header));
    Map<String, Object> body = Map.of("type", "section", "fields", slackFields(click));
    return List.of(headerBlock, body);
  }

  private static List<Map<String, Object>> slackFields(Map<String, Object> click) {
    List<Map<String, Object>> fields = new ArrayList<>();
    addSlackField(fields, "Occurred", isoTimestamp(click.get("occurredAt")));
    addSlackField(fields, "Country", str(click.get("countryCode")));
    addSlackField(fields, "Device", str(click.get("deviceClass")));
    addSlackField(fields, "Channel", str(click.get("channel")));
    addSlackField(fields, "UTM Source", str(click.get("utmSource")));
    if (Boolean.TRUE.equals(click.get("bot"))) {
      addSlackField(fields, "Bot", "yes");
    }
    return fields;
  }

  private static void addSlackField(List<Map<String, Object>> fields, String label, String value) {
    if (value == null || value.isBlank()) return;
    fields.add(Map.of("type", "mrkdwn", "text", "*" + label + ":* " + value));
  }

  private static Map<String, Object> slackBatch(long linkId, List<Map<String, Object>> events) {
    String summary = "kurl batch: " + events.size() + " click(s) for link #" + linkId;
    return Map.of("username", SENDER_NAME, "text", summary);
  }

  private static String clickSummaryLine(Map<String, Object> click) {
    List<String> parts = new ArrayList<>();
    String country = str(click.get("countryCode"));
    String device = str(click.get("deviceClass"));
    String channel = str(click.get("channel"));
    String utm = str(click.get("utmSource"));
    if (!country.isBlank()) parts.add(country);
    if (!device.isBlank()) parts.add(device);
    if (!channel.isBlank()) parts.add("via " + channel);
    if (!utm.isBlank()) parts.add("utm=" + utm);
    if (Boolean.TRUE.equals(click.get("bot"))) parts.add("(bot)");
    return parts.isEmpty() ? "click recorded" : String.join(" · ", parts);
  }

  private static String str(Object v) {
    return v == null ? "" : v.toString();
  }

  private static String isoTimestamp(Object v) {
    String s = str(v);
    if (s.isBlank()) return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    return s;
  }
}
