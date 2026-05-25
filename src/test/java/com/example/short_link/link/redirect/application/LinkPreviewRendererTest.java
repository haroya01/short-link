package com.example.short_link.link.redirect.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LinkPreviewRendererTest {

  private final LinkPreviewRenderer renderer = new LinkPreviewRenderer();

  @Test
  void rendersOgTagsWhenAvailable() {
    LinkEntity link = link("https://example.com/article");
    link.applyOgMetadata("Title", "Some description", "https://example.com/img.png", Instant.now());

    String html = renderer.render(link, "https://kurl.me/abcdefg", 0L);

    assertThat(html).contains("<meta property=\"og:title\" content=\"Title\">");
    assertThat(html).contains("<meta property=\"og:description\" content=\"Some description\">");
    assertThat(html)
        .contains("<meta property=\"og:image\" content=\"https://example.com/img.png\">");
    assertThat(html).contains("<meta property=\"og:url\" content=\"https://kurl.me/abcdefg\">");
    assertThat(html).contains("twitter:card\" content=\"summary_large_image\"");
  }

  @Test
  void fallsBackToOriginalUrlAndDefaultDescription() {
    LinkEntity link = link("https://example.com/article");

    String html = renderer.render(link, "https://kurl.me/abcdefg", 0L);

    assertThat(html).contains("https://example.com/article");
    assertThat(html).contains("Shortened with kurl");
    // No destination og:image → we substitute the kurl-generated card so the share preview
    // still gets a large image, with the click count baked in.
    assertThat(html).contains("twitter:card\" content=\"summary_large_image\"");
    assertThat(html).contains("https://kurl.me/abcdefg/og.png?c=0");
  }

  @Test
  void usesDestinationImageWhenPresent() {
    LinkEntity link = link("https://example.com/article");
    link.applyOgMetadata(null, null, "https://example.com/hero.jpg", Instant.now());

    String html = renderer.render(link, "https://kurl.me/abcdefg", 42L);

    assertThat(html).contains("https://example.com/hero.jpg");
    assertThat(html).doesNotContain("/og.png");
  }

  @Test
  void escapesHtmlSpecialCharacters() {
    LinkEntity link = link("https://example.com/?a=1&b=2");
    link.applyOgMetadata("<script>alert(1)</script>", null, null, Instant.now());

    String html = renderer.render(link, "https://kurl.me/abcdefg", 0L);

    assertThat(html).doesNotContain("<script>alert(1)</script>");
    assertThat(html).contains("&lt;script&gt;");
    assertThat(html).contains("a=1&amp;b=2");
  }

  @Test
  void includesMetaRefreshFallback() {
    LinkEntity link = link("https://example.com/x");
    String html = renderer.render(link, "https://kurl.me/abcdefg", 0L);
    assertThat(html)
        .contains("<meta http-equiv=\"refresh\" content=\"0;url=https://example.com/x\">");
  }

  private static LinkEntity link(String originalUrl) {
    LinkEntity entity = new LinkEntity(originalUrl, "abcdefg");
    setField(entity, "id", 1L);
    setField(entity, "createdAt", Instant.now());
    return entity;
  }

  private static void setField(Object target, String name, Object value) {
    try {
      Class<?> c = target.getClass();
      while (c != null) {
        try {
          Field f = c.getDeclaredField(name);
          f.setAccessible(true);
          f.set(target, value);
          return;
        } catch (NoSuchFieldException ignored) {
          c = c.getSuperclass();
        }
      }
      throw new NoSuchFieldException(name);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
