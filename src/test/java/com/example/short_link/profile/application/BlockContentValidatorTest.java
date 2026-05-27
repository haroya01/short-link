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

  @Test
  void textNormalizesTrimmedBody() {
    String normalized = BlockContentValidator.validate(ProfileBlockType.TEXT, "  hi  ");
    assertThat(normalized).isNotBlank();
  }

  @Test
  void textRejectsNull() {
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.TEXT, null))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void imageAcceptsPlainHttpUrl() {
    assertThat(BlockContentValidator.validate(ProfileBlockType.IMAGE, "http://cdn.example/x.png"))
        .isEqualTo("http://cdn.example/x.png");
  }

  @Test
  void imageRejectsUrlWithoutHost() {
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.IMAGE, "https:///x"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void imageRejectsSchemelessUrl() {
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.IMAGE, "cdn.example"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void embedAcceptsKnownProvider() {
    assertThat(
            BlockContentValidator.validate(
                ProfileBlockType.EMBED, "https://www.youtube.com/watch?v=abc"))
        .contains("youtube");
  }

  @Test
  void embedRejectsOver2048Chars() {
    String huge = "https://www.youtube.com/watch?v=" + "a".repeat(2048);
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.EMBED, huge))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void emailFormRejectsBlank() {
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.EMAIL_FORM, "  "))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void contactCardRejectsBlank() {
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.CONTACT_CARD, ""))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void galleryRejectsOver2048Chars() {
    String huge = "{" + "x".repeat(2049) + "}";
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.GALLERY, huge))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void galleryRejectsBlank() {
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.GALLERY, ""))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void productCardRejectsOver16384Chars() {
    String huge = "x".repeat(16385);
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.PRODUCT_CARD, huge))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void productCardRejectsBlank() {
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.PRODUCT_CARD, ""))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void bookingRejectsOver2048Chars() {
    String huge = "x".repeat(2049);
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.BOOKING, huge))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void bookingRejectsBlank() {
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.BOOKING, ""))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void eventRejectsOver2048Chars() {
    String huge = "x".repeat(2049);
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.EVENT, huge))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void eventRejectsBlank() {
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.EVENT, ""))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void placeRejectsOver2048Chars() {
    String huge = "x".repeat(2049);
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.PLACE, huge))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void placeRejectsBlank() {
    assertThatThrownBy(() -> BlockContentValidator.validate(ProfileBlockType.PLACE, ""))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void emailFormHappyPath() {
    String result =
        BlockContentValidator.validate(
            ProfileBlockType.EMAIL_FORM, "{\"title\":\"뉴스레터\",\"successMessage\":\"감사합니다\"}");
    assertThat(result).isNotBlank();
  }

  @Test
  void contactCardHappyPath() {
    String result =
        BlockContentValidator.validate(
            ProfileBlockType.CONTACT_CARD, "{\"name\":\"홍길동\",\"phone\":\"010-1234-5678\"}");
    assertThat(result).isNotBlank();
  }

  @Test
  void galleryHappyPath() {
    String result =
        BlockContentValidator.validate(
            ProfileBlockType.GALLERY, "{\"images\":[\"https://example.com/x.jpg\"]}");
    assertThat(result).contains("x.jpg");
  }

  @Test
  void bookingHappyPath() {
    String result =
        BlockContentValidator.validate(
            ProfileBlockType.BOOKING, "{\"url\":\"https://calendly.com/me\"}");
    assertThat(result).contains("calendly.com");
  }

  @Test
  void placeHappyPath() {
    String result =
        BlockContentValidator.validate(
            ProfileBlockType.PLACE,
            "{\"name\":\"카페\",\"address\":\"서울\",\"lat\":37.5,\"lng\":127.0}");
    assertThat(result).contains("\"name\":\"카페\"");
  }

  @Test
  void productCardHappyPath() {
    String result =
        BlockContentValidator.validate(
            ProfileBlockType.PRODUCT_CARD, "{\"items\":[{\"name\":\"빵\"}]}");
    assertThat(result).contains("\"name\":\"빵\"");
  }

  @Test
  void eventHappyPath() {
    String result =
        BlockContentValidator.validate(
            ProfileBlockType.EVENT, "{\"title\":\"행사\",\"startsAt\":\"2026-01-01T00:00:00Z\"}");
    assertThat(result).contains("\"title\":\"행사\"");
  }
}
