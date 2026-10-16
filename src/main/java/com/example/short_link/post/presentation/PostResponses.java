package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PostScoped;
import org.springframework.http.ResponseEntity;

final class PostResponses {

  private PostResponses() {}

  static <T> ResponseEntity<T> of(PostScoped<T> scoped) {
    return restricted(ResponseEntity.ok(), scoped.restricted()).body(scoped.body());
  }

  static <T> ResponseEntity<T> list(PostScoped<T> scoped) {
    return scoped.restricted()
        ? ResponseEntity.ok().header("Cache-Control", "private, no-store").body(scoped.body())
        : ResponseEntity.ok(scoped.body());
  }

  static ResponseEntity.BodyBuilder restricted(
      ResponseEntity.BodyBuilder response, boolean restricted) {
    return restricted
        ? response.header("Cache-Control", "private, no-store").header("X-Robots-Tag", "noindex")
        : response;
  }
}
