package com.example.short_link.notification.infrastructure;

import com.example.short_link.notification.application.push.PushRoute;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;

// Mirrors the web blog host's routes (/@{user}/{slug}, /@{user}/series/{slug}, /collections/{id},
// /@{user}/notes/{id})
// and the comment / highlight anchors the post page reads, in the apps' NotificationRoute order.
final class BlogPushUrl {

  private static final String FALLBACK = "/";

  private BlogPushUrl() {}

  static String of(String blogBaseUrl, PushRoute route) {
    if (route == null) {
      return FALLBACK;
    }
    String base =
        blogBaseUrl.endsWith("/")
            ? blogBaseUrl.substring(0, blogBaseUrl.length() - 1)
            : blogBaseUrl;
    if (route.collectionId() != null) {
      return base + "/collections/" + route.collectionId();
    }
    if (route.noteId() != null && filled(route.ownerUsername())) {
      return base + "/@" + segment(route.ownerUsername()) + "/notes/" + route.noteId();
    }
    if (filled(route.postSlug()) && filled(route.ownerUsername())) {
      String post = base + "/@" + segment(route.ownerUsername()) + "/" + segment(route.postSlug());
      if (route.commentId() != null) {
        return post + "#comment-" + route.commentId();
      }
      if (route.highlightId() != null) {
        return post + "?highlightId=" + route.highlightId() + "&thread=1";
      }
      return post;
    }
    if (filled(route.seriesSlug()) && filled(route.ownerUsername())) {
      return base
          + "/@"
          + segment(route.ownerUsername())
          + "/series/"
          + segment(route.seriesSlug());
    }
    if (filled(route.actorUsername())) {
      return base + "/@" + segment(route.actorUsername());
    }
    return FALLBACK;
  }

  private static boolean filled(String value) {
    return value != null && !value.isBlank();
  }

  private static String segment(String value) {
    return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
  }
}
