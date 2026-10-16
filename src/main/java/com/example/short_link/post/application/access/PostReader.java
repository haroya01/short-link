package com.example.short_link.post.application.access;

public record PostReader(
    Long viewerId, String postKey, String grant, String clientIp, boolean serverRender) {

  public static PostReader of(Long viewerId) {
    return new PostReader(viewerId, null, null, null, false);
  }

  public PostReader anonymous() {
    return new PostReader(null, postKey, grant, clientIp, serverRender);
  }

  public PostReader withoutCredentials() {
    return new PostReader(viewerId, null, null, clientIp, serverRender);
  }
}
