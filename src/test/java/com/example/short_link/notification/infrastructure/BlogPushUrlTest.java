package com.example.short_link.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.notification.application.push.PushRoute;
import org.junit.jupiter.api.Test;

class BlogPushUrlTest {

  @Test
  void trailingSlashOnTheBaseDoesNotDoubleUp() {
    assertThat(
            BlogPushUrl.of(
                "https://blog.kurl.me/",
                new PushRoute(null, "me", "p", null, null, null, null, null)))
        .isEqualTo("https://blog.kurl.me/@me/p");
  }

  @Test
  void blankHandlesFallThroughToTheNextTarget() {
    assertThat(
            BlogPushUrl.of(
                "https://blog.kurl.me", new PushRoute("yuki", " ", "p", "s", null, 1L, null, null)))
        .isEqualTo("https://blog.kurl.me/@yuki");
    assertThat(
            BlogPushUrl.of(
                "https://blog.kurl.me",
                new PushRoute(" ", null, null, null, null, null, null, null)))
        .isEqualTo("/");
    assertThat(BlogPushUrl.of("https://blog.kurl.me", null)).isEqualTo("/");
  }

  @Test
  void seriesNeedsItsOwner() {
    assertThat(
            BlogPushUrl.of(
                "https://blog.kurl.me",
                new PushRoute("yuki", null, null, "walks", null, null, null, null)))
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

  @Test
  void eachTargetOpensInTheAppsRouteOrder() {
    String base = "https://blog.kurl.me";

    assertThat(BlogPushUrl.of(base, new PushRoute("yuki", "me", "p", "s", null, null, null, 5L)))
        .isEqualTo(base + "/@me/notes/5");
    assertThat(BlogPushUrl.of(base, new PushRoute("yuki", "me", "p", "s", null, 3L, 4L, null)))
        .isEqualTo(base + "/@me/p#comment-3");
    assertThat(BlogPushUrl.of(base, new PushRoute("yuki", "me", "p", "s", null, null, 4L, null)))
        .isEqualTo(base + "/@me/p?highlightId=4&thread=1");
    assertThat(
            BlogPushUrl.of(
                base, new PushRoute("yuki", "me", null, "walks", null, null, null, null)))
        .isEqualTo(base + "/@me/series/walks");
    assertThat(BlogPushUrl.of(base, new PushRoute("yuki", "me", "p", "s", 9L, 3L, 4L, 5L)))
        .isEqualTo(base + "/collections/9");
  }
}
