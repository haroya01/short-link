package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.ActivityStreams;
import java.util.List;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

final class ActivityPubMedia {

  static final String ACTIVITY_JSON_VALUE = "application/activity+json";
  static final MediaType ACTIVITY_JSON = MediaType.parseMediaType(ACTIVITY_JSON_VALUE);
  static final String CONTEXT = ActivityStreams.CONTEXT;

  private ActivityPubMedia() {}

  // Browsers get the human profile; anything asking for JSON (or nothing in particular) gets the
  // ActivityPub document. A malformed Accept header falls back to JSON rather than failing.
  static boolean wantsHtml(String accept) {
    if (accept == null || accept.isBlank()) {
      return false;
    }
    List<MediaType> accepted;
    try {
      accepted = MediaType.parseMediaTypes(accept);
    } catch (InvalidMediaTypeException malformed) {
      return false;
    }
    boolean html =
        accepted.stream().anyMatch(type -> type.equalsTypeAndSubtype(MediaType.TEXT_HTML));
    boolean json =
        accepted.stream()
            .anyMatch(type -> !type.isWildcardSubtype() && type.getSubtype().endsWith("json"));
    return html && !json;
  }
}
