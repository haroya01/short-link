package com.example.short_link.note.application.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.image.ImageVariantKeys;
import org.junit.jupiter.api.Test;

class RemoteMediaKeysTest {

  private static final String URL = "https://files.example/media/original/a.png";

  @Test
  void aCopyLivesUnderNoteImagesWhereClientsFindItsResizedCopies() {
    String key = RemoteMediaKeys.keyOf(URL, "image/png");

    assertThat(key).matches("note-images/remote/[0-9a-f]{64}\\.png");
    assertThat(ImageVariantKeys.eligible(key)).isTrue();
    assertThat(ImageVariantKeys.variantKey(key, ImageVariantKeys.SMALL)).endsWith(".w480.png");
    assertThat(RemoteMediaKeys.keyOf(URL, "image/jpeg")).endsWith(".jpg");
  }

  @Test
  void oneAddressHasOneKey() {
    assertThat(RemoteMediaKeys.keyOf(URL, "image/png"))
        .isEqualTo(RemoteMediaKeys.keyOf(URL, "image/png"))
        .isNotEqualTo(RemoteMediaKeys.keyOf(URL + "?v=2", "image/png"));
  }

  @Test
  void onlyAJpegOrPngOverHttpsIsCopied() {
    assertThat(RemoteMediaKeys.copyable(URL, "image/png")).isTrue();
    assertThat(RemoteMediaKeys.copyable("HTTPS://files.example/a.jpg", " Image/JPEG ")).isTrue();
    assertThat(RemoteMediaKeys.type(" Image/JPEG ")).isEqualTo("image/jpeg");

    assertThat(RemoteMediaKeys.copyable("http://files.example/a.png", "image/png")).isFalse();
    assertThat(RemoteMediaKeys.copyable(URL, "image/gif")).isFalse();
    assertThat(RemoteMediaKeys.copyable(URL, "image/webp")).isFalse();
    assertThat(RemoteMediaKeys.copyable(URL, "video/mp4")).isFalse();
    assertThat(RemoteMediaKeys.copyable(URL, null)).isFalse();
    assertThat(RemoteMediaKeys.copyable(null, "image/png")).isFalse();
  }
}
