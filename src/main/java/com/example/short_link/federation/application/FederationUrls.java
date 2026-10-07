package com.example.short_link.federation.application;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

// Every federation path sits two or more segments deep so it can never collide with a short code
// (one segment, 3–16 alphanumerics).
@Component
@RequiredArgsConstructor
public class FederationUrls {

  public static final String ACTOR_PATH = "/ap/actors/";
  private static final String NOTE_PATH = "/ap/notes/";

  private static final Pattern PUBLIC_ID = Pattern.compile("[a-z0-9]{1,32}");
  private static final Pattern NOTE_ID = Pattern.compile("[1-9][0-9]{0,17}");

  private final FederationProperties props;

  public String domain() {
    return props.domain();
  }

  public String actor(String publicId) {
    return props.baseUrl() + ACTOR_PATH + publicId;
  }

  public Optional<String> publicIdOf(String actorUri) {
    String prefix = props.baseUrl() + ACTOR_PATH;
    if (actorUri == null || !actorUri.startsWith(prefix)) {
      return Optional.empty();
    }
    String publicId = actorUri.substring(prefix.length());
    return PUBLIC_ID.matcher(publicId).matches() ? Optional.of(publicId) : Optional.empty();
  }

  public boolean isFollow(String activityId) {
    int mark = activityId == null ? -1 : activityId.indexOf("#follows/");
    return mark > 0 && publicIdOf(activityId.substring(0, mark)).isPresent();
  }

  public String key(String publicId) {
    return actor(publicId) + "#main-key";
  }

  public String inbox(String publicId) {
    return actor(publicId) + "/inbox";
  }

  public String outbox(String publicId) {
    return actor(publicId) + "/outbox";
  }

  public String followers(String publicId) {
    return actor(publicId) + "/followers";
  }

  public String featured(String publicId) {
    return actor(publicId) + "/featured";
  }

  public String following(String publicId) {
    return actor(publicId) + "/following";
  }

  public String instance() {
    return props.baseUrl() + "/ap/instance";
  }

  public String instanceKey() {
    return instance() + "#main-key";
  }

  public String sharedInbox() {
    return props.baseUrl() + "/ap/inbox";
  }

  public String note(Long noteId) {
    return props.baseUrl() + NOTE_PATH + noteId;
  }

  public Optional<Long> noteIdOf(String noteUri) {
    String prefix = props.baseUrl() + NOTE_PATH;
    if (noteUri == null || !noteUri.startsWith(prefix)) {
      return Optional.empty();
    }
    String noteId = noteUri.substring(prefix.length());
    return NOTE_ID.matcher(noteId).matches() ? Optional.of(Long.valueOf(noteId)) : Optional.empty();
  }

  public String repost(Long repostId) {
    return props.baseUrl() + "/ap/reposts/" + repostId;
  }

  public String notePage(String username, Long noteId) {
    return profile(username) + "/notes/" + noteId;
  }

  public String tag(String name) {
    return props.profileBaseUrl()
        + "/tags/"
        + UriUtils.encodePathSegment(name, StandardCharsets.UTF_8)
        + "?view=notes";
  }

  public String blogPost(String username, String slug) {
    return profile(username) + "/" + UriUtils.encodePathSegment(slug, StandardCharsets.UTF_8);
  }

  public String webFingerTemplate() {
    return props.baseUrl() + "/.well-known/webfinger?resource={uri}";
  }

  public String nodeInfo() {
    return props.baseUrl() + "/ap/nodeinfo/2.1";
  }

  public String profile(String username) {
    return props.profileBaseUrl()
        + "/@"
        + UriUtils.encodePathSegment(username, StandardCharsets.UTF_8);
  }
}
