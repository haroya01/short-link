package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.cache.PolymorphicJsonRedisSerializer;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.dto.CachedLinkFixture;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CachedLinkCacheJsonTest {

  private final PolymorphicJsonRedisSerializer serializer =
      new PolymorphicJsonRedisSerializer(
          PolymorphicJsonRedisSerializer.objectMapper("com.example.short_link."));

  private static CachedLink link(CachedLink.VisitOptions options) {
    return CachedLinkFixture.link(new LinkId(7L), "https://dest.example.com")
        .shortCode(new ShortCode("abc1234"))
        .userId(1L)
        .visitOptions(options)
        .build();
  }

  @Test
  void visitOptionsSurviveTheCache() {
    CachedLink restored =
        (CachedLink)
            serializer.deserialize(
                serializer.serialize(link(new CachedLink.VisitOptions(true, null, null))));

    assertThat(restored.visitOptions().openInBrowser()).isTrue();
  }

  @Test
  void aSplashSurvivesTheCache() {
    CachedLink.Splash splash = new CachedLink.Splash("쿠폰", 2, "앱 받기", "https://kurl.me/app1");
    CachedLink restored =
        (CachedLink)
            serializer.deserialize(
                serializer.serialize(link(new CachedLink.VisitOptions(false, splash, null))));

    assertThat(restored.visitOptions().splash()).isEqualTo(splash);
  }

  @Test
  void entriesCachedBeforeTheSplashExistedStillLoad() {
    String json =
        new String(
            serializer.serialize(link(new CachedLink.VisitOptions(true, null, null))),
            StandardCharsets.UTF_8);
    String legacy = json.replace(",\"splash\":null", "");
    assertThat(legacy).doesNotContain("splash");

    CachedLink restored =
        (CachedLink) serializer.deserialize(legacy.getBytes(StandardCharsets.UTF_8));

    assertThat(restored.visitOptions().openInBrowser()).isTrue();
    assertThat(restored.visitOptions().splash()).isNull();
  }

  @Test
  void entriesCachedBeforeTheOpeningTimeExistedStillLoad() {
    String json =
        new String(
            serializer.serialize(link(new CachedLink.VisitOptions(true, null, null))),
            StandardCharsets.UTF_8);
    String legacy = json.replace(",\"opensAt\":null", "");
    assertThat(legacy).doesNotContain("opensAt");

    CachedLink restored =
        (CachedLink) serializer.deserialize(legacy.getBytes(StandardCharsets.UTF_8));

    assertThat(restored.visitOptions().opensAt()).isNull();
    assertThat(restored.visitOptions().openInBrowser()).isTrue();
  }

  @Test
  void entriesCachedBeforeVisitOptionsExistedStillLoad() {
    String json =
        new String(
            serializer.serialize(link(new CachedLink.VisitOptions(true, null, null))),
            StandardCharsets.UTF_8);
    String legacy = json.replaceAll(",\"visitOptions\":\\{[^}]*\\}", "");
    assertThat(legacy).doesNotContain("visitOptions");

    CachedLink restored =
        (CachedLink) serializer.deserialize(legacy.getBytes(StandardCharsets.UTF_8));

    assertThat(restored.visitOptions()).isEqualTo(CachedLink.VisitOptions.NONE);
    assertThat(restored.originalUrl()).isEqualTo("https://dest.example.com");
  }

  @Test
  void entriesCachedWithTheFetchedPreviewStillLoad() {
    String cachedByThePreviousImage =
        "{\"@class\":\"com.example.short_link.link.application.dto.CachedLink\",\"linkId\":[\"com.example.short_link.link.domain.LinkId\",7],\"shortCode\":[\"com.example.short_link.link.domain.ShortCode\",\"abc1234\"],\"userId\":[\"java.lang.Long\",1],\"originalUrl\":\"https://dest.example.com\",\"expiresAt\":[\"java.time.Instant\",1796083200.000000000],\"ogTitle\":\"t\",\"ogDescription\":\"d\",\"ogImage\":\"https://img\",\"blockedCountries\":\"KR,JP\",\"passwordRequired\":false,\"maxViews\":null,\"expiredMessage\":null,\"variants\":[\"java.util.ImmutableCollections$ListN\",[]],\"visitOptions\":{\"@class\":\"com.example.short_link.link.application.dto.CachedLink$VisitOptions\",\"openInBrowser\":true,\"splash\":null,\"opensAt\":null},\"disabled\":false,\"statsPublic\":false}";

    CachedLink restored =
        (CachedLink)
            serializer.deserialize(cachedByThePreviousImage.getBytes(StandardCharsets.UTF_8));

    assertThat(restored.shortCode()).isEqualTo(new ShortCode("abc1234"));
    assertThat(restored.blockedCountries()).isEqualTo("KR,JP");
    assertThat(restored.visitOptions().openInBrowser()).isTrue();
  }
}
