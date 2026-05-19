package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class OgCardImageRendererTest {

  private final OgCardImageRenderer renderer = new OgCardImageRenderer();

  @Test
  void rendersValidPng() throws IOException {
    byte[] png = renderer.render("https://kurl.me/abc", 42L);
    assertThat(png).isNotEmpty();
    assertThat(png[0]).isEqualTo((byte) 0x89);
    assertThat(png[1]).isEqualTo((byte) 'P');
    assertThat(png[2]).isEqualTo((byte) 'N');
    assertThat(png[3]).isEqualTo((byte) 'G');
    var img = ImageIO.read(new ByteArrayInputStream(png));
    assertThat(img.getWidth()).isEqualTo(1200);
    assertThat(img.getHeight()).isEqualTo(630);
  }

  @Test
  void singularClickLabel() throws IOException {
    byte[] png = renderer.render("https://kurl.me/abc", 1L);
    assertThat(png).isNotEmpty();
  }

  @Test
  void zeroClicksWorks() throws IOException {
    byte[] png = renderer.render("https://kurl.me/abc", 0L);
    assertThat(png).isNotEmpty();
  }

  @Test
  void negativeClickClampedToZero() throws IOException {
    byte[] png = renderer.render("https://kurl.me/abc", -5L);
    assertThat(png).isNotEmpty();
  }

  @Test
  void thousandsBucketsRender() throws IOException {
    assertThat(renderer.render("https://kurl.me/abc", 1_500L)).isNotEmpty();
    assertThat(renderer.render("https://kurl.me/abc", 1_000L)).isNotEmpty();
    assertThat(renderer.render("https://kurl.me/abc", 12_000L)).isNotEmpty();
  }

  @Test
  void millionsBucketsRender() throws IOException {
    assertThat(renderer.render("https://kurl.me/abc", 2_500_000L)).isNotEmpty();
    assertThat(renderer.render("https://kurl.me/abc", 2_000_000L)).isNotEmpty();
  }

  @Test
  void veryLongShortUrlTruncates() throws IOException {
    String longUrl = "https://kurl.me/" + "x".repeat(200);
    assertThat(renderer.render(longUrl, 100L)).isNotEmpty();
  }

  @Test
  void nullShortUrlDoesNotThrow() throws IOException {
    assertThat(renderer.render(null, 100L)).isNotEmpty();
  }
}
