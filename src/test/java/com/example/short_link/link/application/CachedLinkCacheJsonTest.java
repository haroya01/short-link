package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.cache.PolymorphicJsonRedisSerializer;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CachedLinkCacheJsonTest {

  private final PolymorphicJsonRedisSerializer serializer =
      new PolymorphicJsonRedisSerializer(
          PolymorphicJsonRedisSerializer.objectMapper("com.example.short_link."));

  private static CachedLink link(CachedLink.VisitOptions options) {
    return new CachedLink(
        new LinkId(7L),
        new ShortCode("abc1234"),
        1L,
        "https://dest.example.com",
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        List.of(),
        options);
  }

  @Test
  void visitOptionsSurviveTheCache() {
    CachedLink restored =
        (CachedLink)
            serializer.deserialize(serializer.serialize(link(new CachedLink.VisitOptions(true))));

    assertThat(restored.visitOptions().openInBrowser()).isTrue();
  }

  @Test
  void aSplashSurvivesTheCache() {
    CachedLink.Splash splash = new CachedLink.Splash("쿠폰", 2, "앱 받기", "https://kurl.me/app1");
    CachedLink restored =
        (CachedLink)
            serializer.deserialize(
                serializer.serialize(link(new CachedLink.VisitOptions(false, splash))));

    assertThat(restored.visitOptions().splash()).isEqualTo(splash);
  }

  @Test
  void entriesCachedBeforeTheSplashExistedStillLoad() {
    String json =
        new String(
            serializer.serialize(link(new CachedLink.VisitOptions(true))), StandardCharsets.UTF_8);
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
            serializer.serialize(link(new CachedLink.VisitOptions(true))), StandardCharsets.UTF_8);
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
            serializer.serialize(link(new CachedLink.VisitOptions(true))), StandardCharsets.UTF_8);
    String legacy = json.replaceAll(",\"visitOptions\":\\{[^}]*\\}", "");
    assertThat(legacy).doesNotContain("visitOptions");

    CachedLink restored =
        (CachedLink) serializer.deserialize(legacy.getBytes(StandardCharsets.UTF_8));

    assertThat(restored.visitOptions()).isEqualTo(CachedLink.VisitOptions.NONE);
    assertThat(restored.originalUrl()).isEqualTo("https://dest.example.com");
  }
}
