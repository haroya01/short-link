package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.OgMetadata;
import org.junit.jupiter.api.Test;

class OgMetadataTest {

  @Test
  void emptyHasNoneOfTheFields() {
    OgMetadata m = OgMetadata.empty();
    assertThat(m.hasAny()).isFalse();
    assertThat(m.title()).isNull();
    assertThat(m.description()).isNull();
    assertThat(m.image()).isNull();
  }

  @Test
  void hasAnyReturnsTrueWhenAtLeastOneFieldFilled() {
    assertThat(new OgMetadata("title", null, null).hasAny()).isTrue();
    assertThat(new OgMetadata(null, "desc", null).hasAny()).isTrue();
    assertThat(new OgMetadata(null, null, "img").hasAny()).isTrue();
  }

  @Test
  void blankFieldsCountAsAbsent() {
    assertThat(new OgMetadata("", "  ", null).hasAny()).isFalse();
  }
}
