package com.example.short_link.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.notification.application.push.PushRoute;
import org.junit.jupiter.api.Test;

class BlogPushUrlTest {

  @Test
  void trailingSlashOnTheBaseDoesNotDoubleUp() {
    assertThat(
            BlogPushUrl.of(
                "https://blog.kurl.me/", new PushRoute(null, "me", "p", null, null, null, null)))
        .isEqualTo("https://blog.kurl.me/@me/p");
  }

  @Test
  void blankHandlesFallThroughToTheNextTarget() {
    assertThat(
            BlogPushUrl.of(
                "https://blog.kurl.me", new PushRoute("yuki", " ", "p", "s", null, 1L, null)))
        .isEqualTo("https://blog.kurl.me/@yuki");
    assertThat(
            BlogPushUrl.of(
                "https://blog.kurl.me", new PushRoute(" ", null, null, null, null, null, null)))
        .isEqualTo("/");
    assertThat(BlogPushUrl.of("https://blog.kurl.me", null)).isEqualTo("/");
  }

  @Test
  void seriesNeedsItsOwner() {
    assertThat(
            BlogPushUrl.of(
                "https://blog.kurl.me",
                new PushRoute("yuki", null, null, "walks", null, null, null)))
        .isEqualTo("https://blog.kurl.me/@yuki");
  }

  @Test
  void aNoteOpensUnderItsWriter() {
    assertThat(
            BlogPushUrl.of(
                "https://blog.kurl.me",
                new PushRoute("yuki", "me", null, null, null, null, null, 5L)))
        .isEqualTo("https://blog.kurl.me/@me/notes/5");
  }
}
