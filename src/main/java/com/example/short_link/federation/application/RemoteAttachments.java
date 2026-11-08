package com.example.short_link.federation.application;

import static com.example.short_link.federation.application.ActivityStreams.MAX_URI;
import static com.example.short_link.federation.application.ActivityStreams.sameHost;
import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.common.note.RemoteNotes.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

final class RemoteAttachments {

  private RemoteAttachments() {}

  // The page people open: an http(s) address on the object's own server, or none.
  static String address(JsonNode url, String uri) {
    String href = webLink(url);
    return href != null && uri != null && sameHost(uri, href) ? href : null;
  }

  // PeerTube lists the watch page beside the video files; the text/html link is the page.
  private static String webLink(JsonNode url) {
    if (url != null && url.isArray()) {
      for (JsonNode item : url) {
        if (String.valueOf(text(item.get("mediaType"))).startsWith("text/html")) {
          return text(item.get("href"));
        }
      }
    }
    return link(url);
  }

  // PeerTube sends a small thumbnail and a wider preview as a video's icons.
  static List<RemoteNotes.Media> cover(JsonNode object, Kind kind) {
    List<RemoteNotes.Media> pictures = new ArrayList<>();
    if (kind == Kind.VIDEO) {
      pictures.addAll(pictures(object.get("icon")));
      pictures.sort(
          Comparator.comparing(
                  (RemoteNotes.Media picture) -> picture.width() == null ? 0 : picture.width())
              .reversed());
    }
    pictures.addAll(pictures(object.get("image")));
    if (kind != Kind.VIDEO) {
      pictures.addAll(pictures(object.get("attachment")));
    }
    return pictures.isEmpty() ? List.of() : List.of(pictures.getFirst());
  }

  private static List<RemoteNotes.Media> pictures(JsonNode node) {
    String bare = text(node);
    if (bare != null) {
      return bare.startsWith("https://") && bare.length() <= MAX_URI
          ? List.of(new RemoteNotes.Media(bare, null, "image/jpeg"))
          : List.of();
    }
    return media(node).stream().filter(media -> media.contentType().startsWith("image/")).toList();
  }

  static List<RemoteNotes.Media> media(JsonNode attachments) {
    List<RemoteNotes.Media> media = new ArrayList<>();
    if (attachments == null) {
      return media;
    }
    Iterable<JsonNode> items = attachments.isArray() ? attachments : List.of(attachments);
    for (JsonNode attachment : items) {
      String type = mediaType(attachment);
      String url = link(attachment.get("url"));
      if (type != null && url != null && url.startsWith("https://") && url.length() <= MAX_URI) {
        media.add(
            new RemoteNotes.Media(
                url,
                text(attachment.get("name")),
                type,
                side(attachment.get("width")),
                side(attachment.get("height"))));
      }
    }
    return media;
  }

  private static Integer side(JsonNode node) {
    return node != null && node.canConvertToInt() && node.isIntegralNumber()
        ? node.intValue()
        : null;
  }

  // Pictures, video (Mastodon's GIFs arrive as silent mp4) and audio stay on their server and play
  // from there; anything else is left out.
  private static String mediaType(JsonNode attachment) {
    String declared = text(attachment.get("mediaType"));
    if (declared != null) {
      String type = declared.toLowerCase(Locale.ROOT);
      return type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/")
          ? type
          : null;
    }
    return switch (String.valueOf(text(attachment.get("type")))) {
      case "Image" -> "image/jpeg";
      case "Video" -> "video/mp4";
      case "Audio" -> "audio/mpeg";
      default -> null;
    };
  }

  private static String link(JsonNode node) {
    if (node == null) {
      return null;
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        String href = link(item);
        if (href != null) {
          return href;
        }
      }
      return null;
    }
    if (node.isObject()) {
      String href = text(node.get("href"));
      return href != null ? href : text(node.get("url"));
    }
    return text(node);
  }
}
