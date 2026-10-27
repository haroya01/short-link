package com.example.short_link.post.application.access;

public record PostViewer(
    Long viewerId, String postKey, String grant, String clientIp, boolean serverRender) {

  public static PostViewer of(Long viewerId) {
    return new PostViewer(viewerId, null, null, null, false);
  }

  public PostViewer anonymous() {
    return new PostViewer(null, postKey, grant, clientIp, serverRender);
  }

  public PostViewer withoutCredentials() {
    return new PostViewer(viewerId, null, null, clientIp, serverRender);
  }
}
