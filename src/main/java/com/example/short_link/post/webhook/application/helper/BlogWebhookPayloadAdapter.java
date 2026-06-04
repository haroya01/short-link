package com.example.short_link.post.webhook.application.helper;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.post.webhook.domain.BlogWebhookFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shapes a blog interaction into the body each receiver expects. GENERIC emits structured JSON for
 * self-hosted consumers (verified by the HMAC signature); DISCORD/SLACK emit the chat-native shapes
 * with a one-line human summary. Pure functions over the event — no I/O, no entity.
 */
public final class BlogWebhookPayloadAdapter {

  private static final int BRAND_GREEN = 0x059669;

  private BlogWebhookPayloadAdapter() {}

  /** Header value for {@code X-Kurl-Event}: e.g. "like", "series_subscribe". */
  public static String eventType(BlogInteractionType type) {
    return type.name().toLowerCase();
  }

  public static Map<String, Object> build(
      BlogWebhookFormat format, BlogInteractionEvent event, String actor) {
    return switch (format) {
      case DISCORD -> discord(event, actor);
      case SLACK -> slack(event, actor);
      case GENERIC -> generic(event, actor);
    };
  }

  private static Map<String, Object> generic(BlogInteractionEvent event, String actor) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", eventType(event.type()));
    body.put("actor", actor);
    body.put("occurredAt", event.occurredAt().toString());
    if (event.postId() != null) {
      Map<String, Object> post = new LinkedHashMap<>();
      post.put("id", event.postId());
      post.put("slug", event.postSlug() == null ? "" : event.postSlug());
      post.put("title", event.postTitle() == null ? "" : event.postTitle());
      body.put("post", post);
    }
    if (event.seriesId() != null) {
      Map<String, Object> series = new LinkedHashMap<>();
      series.put("id", event.seriesId());
      series.put("title", event.seriesTitle() == null ? "" : event.seriesTitle());
      body.put("series", series);
    }
    return body;
  }

  private static Map<String, Object> discord(BlogInteractionEvent event, String actor) {
    Map<String, Object> embed = new LinkedHashMap<>();
    embed.put("title", titleFor(event.type()));
    embed.put("description", summary(event, actor));
    embed.put("color", BRAND_GREEN);
    embed.put("timestamp", event.occurredAt().toString());
    List<Map<String, Object>> embeds = new ArrayList<>();
    embeds.add(embed);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("username", "kurl");
    body.put("embeds", embeds);
    return body;
  }

  private static Map<String, Object> slack(BlogInteractionEvent event, String actor) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("text", summary(event, actor));
    return body;
  }

  private static String titleFor(BlogInteractionType type) {
    return switch (type) {
      case LIKE -> "New like";
      case COMMENT -> "New comment";
      case FOLLOW -> "New follower";
      case SERIES_SUBSCRIBE -> "New series subscriber";
    };
  }

  private static String summary(BlogInteractionEvent event, String actor) {
    String who = "@" + (actor == null ? "someone" : actor);
    return switch (event.type()) {
      case LIKE -> who + " liked your post \"" + safe(event.postTitle()) + "\"";
      case COMMENT -> who + " commented on your post \"" + safe(event.postTitle()) + "\"";
      case FOLLOW -> who + " started following you";
      case SERIES_SUBSCRIBE ->
          who + " subscribed to your series \"" + safe(event.seriesTitle()) + "\"";
    };
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }
}
