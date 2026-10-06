package com.example.short_link.note.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoteLinksTest {

  @Test
  void theFirstAddressIsPreviewedWithoutTrailingPunctuation() {
    assertThat(
            NoteLinks.previewUrl(
                "read https://a.example/x?y=1). and https://b.example", false, false))
        .isEqualTo("https://a.example/x?y=1");
    assertThat(NoteLinks.previewUrl("http://a.example/,", false, false))
        .isEqualTo("http://a.example/");
  }

  @Test
  void noAddressPhotosOrAQuoteMeanNoCard() {
    assertThat(NoteLinks.previewUrl("no address here", false, false)).isNull();
    assertThat(NoteLinks.previewUrl(null, false, false)).isNull();
    assertThat(NoteLinks.previewUrl("https://a.example", true, false)).isNull();
    assertThat(NoteLinks.previewUrl("https://a.example", false, true)).isNull();
    assertThat(NoteLinks.previewUrl("https://a.example/" + "x".repeat(2100), false, false))
        .isNull();
  }

  @Test
  void onlyBodiesWithAnAddressAreLookedUp() {
    assertThat(NoteLinks.mayHavePreview("see https://a.example")).isTrue();
    assertThat(NoteLinks.mayHavePreview("plain")).isFalse();
    assertThat(NoteLinks.mayHavePreview(null)).isFalse();
  }
}
