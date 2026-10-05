package com.example.short_link.federation.application;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

// Every federation path sits two or more segments deep so it can never collide with a short code
// (one segment, 3–16 alphanumerics).
@Component
@RequiredArgsConstructor
public class FederationUrls {

  public static final String ACTOR_PATH = "/ap/actors/";

  private final FederationProperties props;

  public String domain() {
    return props.domain();
  }

  public String actor(String publicId) {
    return props.baseUrl() + ACTOR_PATH + publicId;
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

  public String following(String publicId) {
    return actor(publicId) + "/following";
  }

  public String sharedInbox() {
    return props.baseUrl() + "/ap/inbox";
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
