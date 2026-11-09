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
    String owned = filled(route.ownerUsername()) ? owned(base, route) : null;
    if (owned != null) {
      return owned;
    }
    if (filled(route.actorUsername())) {
      return base + "/@" + segment(route.actorUsername());
    }
    return FALLBACK;
  }

  private static String owned(String base, PushRoute route) {
    String owner = base + "/@" + segment(route.ownerUsername());
    if (route.noteId() != null) {
      return owner + "/notes/" + route.noteId();
    }
    if (filled(route.postSlug())) {
      return post(owner + "/" + segment(route.postSlug()), route);
    }
    if (filled(route.seriesSlug())) {
      return owner + "/series/" + segment(route.seriesSlug());
    }
    return null;
  }

  private static String post(String post, PushRoute route) {
    if (route.commentId() != null) {
      return post + "#comment-" + route.commentId();
    }
    if (route.highlightId() != null) {
      return post + "?highlightId=" + route.highlightId() + "&thread=1";
    }
    return post;
  }

  private static boolean filled(String value) {
    return value != null && !value.isBlank();
  }

  private static String segment(String value) {
    return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
  }
}
