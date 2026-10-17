package com.example.short_link.common.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ImageVariantKeysTest {

  @Test
  void variantsSitNextToTheOriginalWithTheirWidthBeforeTheExtension() {
    String original = "note-images/7/cc87a025-0b5e-4c1e-9d7f-2a1b3c4d5e6f.jpg";

    assertThat(ImageVariantKeys.variantsOf(original))
        .containsExactly(
            "note-images/7/cc87a025-0b5e-4c1e-9d7f-2a1b3c4d5e6f.w1080.jpg",
            "note-images/7/cc87a025-0b5e-4c1e-9d7f-2a1b3c4d5e6f.w480.jpg");
  }

  @Test
  void jpegBecomesJpgAndPngStaysPng() {
    assertThat(ImageVariantKeys.variantKey("post-images/7/42/a.jpeg", 480))
        .isEqualTo("post-images/7/42/a.w480.jpg");
    assertThat(ImageVariantKeys.variantKey("post-images/7/42/a.JPG", 1080))
        .isEqualTo("post-images/7/42/a.w1080.jpg");
    assertThat(ImageVariantKeys.variantKey("post-images/7/42/a.png", 1080))
        .isEqualTo("post-images/7/42/a.w1080.png");
    assertThat(ImageVariantKeys.contentType("post-images/7/42/a.png")).isEqualTo("image/png");
    assertThat(ImageVariantKeys.contentType("post-images/7/42/a.jpeg")).isEqualTo("image/jpeg");
  }

  @Test
  void onlyJpegAndPngPhotosOfNotesAndPostsGetVariants() {
    assertThat(ImageVariantKeys.eligible("note-images/7/a.jpg")).isTrue();
    assertThat(ImageVariantKeys.eligible("note-images/7/a.jpeg")).isTrue();
    assertThat(ImageVariantKeys.eligible("post-images/7/42/a.png")).isTrue();
    assertThat(ImageVariantKeys.eligible("post-images/7/42/a.PNG")).isTrue();

    assertThat(ImageVariantKeys.eligible("note-images/7/a.gif")).isFalse();
    assertThat(ImageVariantKeys.eligible("note-images/7/a.webp")).isFalse();
    assertThat(ImageVariantKeys.eligible("avatars/7/a.jpg")).isFalse();
    assertThat(ImageVariantKeys.eligible("banners/7/a.jpg")).isFalse();
    assertThat(ImageVariantKeys.eligible("profile-images/7/a.jpg")).isFalse();
    assertThat(ImageVariantKeys.eligible("event-covers/7/a.jpg")).isFalse();
    assertThat(ImageVariantKeys.eligible("x/note-images/7/a.jpg")).isFalse();
    assertThat(ImageVariantKeys.eligible("note-images/7/a.w1080.jpg")).isFalse();
    assertThat(ImageVariantKeys.eligible("note-images/7/a.w480.png")).isFalse();
    assertThat(ImageVariantKeys.eligible("")).isFalse();
    assertThat(ImageVariantKeys.eligible(null)).isFalse();
    assertThat(ImageVariantKeys.variantsOf("note-images/7/a.gif")).isEmpty();
    assertThatThrownBy(() -> ImageVariantKeys.variantKey("avatars/7/a.jpg", 480))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void keysAreFoundInsideStoredUrlsAndMarkdown() {
    String body =
        "{\"url\":\"https://cdn.kurl.me/post-images/7/42/aa-11.png\"}"
            + " ![x](https://cdn.kurl.me/post-images/7/42/bb-22.jpg \"t\")"
            + " https://cdn.kurl.me/post-images/7/42/aa-11.png"
            + " https://cdn.kurl.me/post-images/7/42/cc.w1080.jpg"
            + " https://cdn.kurl.me/post-images/7/42/dd.gif"
            + " https://cdn.kurl.me/avatars/7/ee.jpg"
            + " xpost-images/7/42/ff.jpg";

    assertThat(ImageVariantKeys.keysIn(body))
        .containsExactly("post-images/7/42/aa-11.png", "post-images/7/42/bb-22.jpg");
    assertThat(ImageVariantKeys.keysIn(null)).isEmpty();
    assertThat(ImageVariantKeys.keysIn("")).isEmpty();
  }
}
