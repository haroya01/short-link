package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PostMetadataValidationTest {

  @ParameterizedTest
  @ValueSource(strings = {"1", "9", "12", "ab", "my-post"})
  void aPostNumberMayBeASingleDigit(String slug) {
    assertThatCode(() -> PostMetadataValidation.requireSlug(slug, "invalid"))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "-", "A1", "a b"})
  void otherShortOrMalformedSlugsAreRefused(String slug) {
    assertThatThrownBy(() -> PostMetadataValidation.requireSlug(slug, "invalid"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
