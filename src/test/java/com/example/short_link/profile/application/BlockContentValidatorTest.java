package com.example.short_link.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.exception.ProfileException;
import org.junit.jupiter.api.Test;

class BlockContentValidatorTest {

  @Test
  void dividerReturnsNullRegardlessOfContent() {
    assertThat(BlockContentValidator.validate(ProfileBlockType.DIVIDER, "ignored")).isNull();
  }

  @Test
  void imageRequiresHttpsUrl() {
    assertThat(BlockContentValidator.validate(ProfileBlockType.IMAGE, "https://cdn.example/x.png"))
        .isEqualTo("https://cdn.example/x.png");
  }

  @Test
  void imageRejectsBlankUrl() {
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.IMAGE, "  "))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void imageRejectsFtpScheme() {
    assertThatThrownBy(
            () -> BlockContentValidator.validate(ProfileBlockType.IMAGE, "ftp://x.com/a.png"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void imageRejectsMalformedUri() {
    assertThatThrownBy(
            () -> BlockContentValidator.validate(ProfileBlockType.IMAGE, "https:// bad uri"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void imageRejectsOver2048Chars() {
    String huge = "https://x.com/" + "a".repeat(2048);
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.IMAGE, huge))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void embedRejectsUnsupportedProvider() {
    assertThatThrownBy(
            () ->
                BlockContentValidator.validate(
                    ProfileBlockType.EMBED, "https://random.example.com/x"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void embedRejectsBlank() {
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.EMBED, "  "))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void emailFormRejectsOver2048Chars() {
    String huge = "{" + "\"a\":\"b\",".repeat(300) + "}";
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.EMAIL_FORM, huge))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void contactCardRejectsOver2048Chars() {
    String huge = "x".repeat(2049);
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.CONTACT_CARD, huge))
        .isInstanceOf(ProfileException.class);
  }
}
