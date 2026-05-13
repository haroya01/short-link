package com.example.short_link.profile.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.application.InvalidUsernameException;
import org.junit.jupiter.api.Test;

class ProductCardCarouselTest {

  @Test
  void normalizesValidPayload() {
    String out =
        ProductCardCarousel.normalize(
            "{\"title\":\"이번 주 케이크\",\"items\":["
                + "{\"name\":\"  딸기 생크림  \","
                + "\"images\":[{\"url\":\"https://img.example/1.jpg\",\"focalX\":40,\"focalY\":30}],"
                + "\"price\":\"45,000원\",\"description\":\"1호, 24h 전 예약\","
                + "\"ctaLabel\":\"주문\",\"ctaUrl\":\"https://pf.kakao.com/_abc\"}]}");
    assertThat(out).contains("\"title\":\"이번 주 케이크\"");
    assertThat(out).contains("\"name\":\"딸기 생크림\"");
    assertThat(out).contains("\"url\":\"https://img.example/1.jpg\"");
    assertThat(out).contains("\"focalX\":40");
    assertThat(out).contains("\"focalY\":30");
    assertThat(out).contains("\"ctaUrl\":\"https://pf.kakao.com/_abc\"");
    // Legacy field is dropped from output even if it sneaks in.
    assertThat(out).doesNotContain("\"image\":");
  }

  @Test
  void legacySingleImageFieldStillAccepted() {
    String out =
        ProductCardCarousel.normalize(
            "{\"items\":[{\"name\":\"x\",\"image\":\"https://img.example/1.jpg\"}]}");
    // Old shape gets wrapped into a 1-element images list with default focal (50/50).
    assertThat(out).contains("\"url\":\"https://img.example/1.jpg\"");
    assertThat(out).contains("\"focalX\":50");
    assertThat(out).contains("\"focalY\":50");
  }

  @Test
  void explicitImagesWinsOverLegacyImage() {
    String out =
        ProductCardCarousel.normalize(
            "{\"items\":[{\"name\":\"x\","
                + "\"image\":\"https://img.example/legacy.jpg\","
                + "\"images\":[{\"url\":\"https://img.example/new.jpg\"}]}]}");
    assertThat(out).contains("\"url\":\"https://img.example/new.jpg\"");
    assertThat(out).doesNotContain("legacy.jpg");
  }

  @Test
  void multipleImagesPreserveOrder() {
    String out =
        ProductCardCarousel.normalize(
            "{\"items\":[{\"name\":\"x\",\"images\":["
                + "{\"url\":\"https://img.example/1.jpg\"},"
                + "{\"url\":\"https://img.example/2.jpg\"},"
                + "{\"url\":\"https://img.example/3.jpg\"}]}]}");
    int idx1 = out.indexOf("1.jpg");
    int idx2 = out.indexOf("2.jpg");
    int idx3 = out.indexOf("3.jpg");
    assertThat(idx1).isGreaterThan(-1);
    assertThat(idx2).isGreaterThan(idx1);
    assertThat(idx3).isGreaterThan(idx2);
  }

  @Test
  void focalPointsClampedToValidRange() {
    String out =
        ProductCardCarousel.normalize(
            "{\"items\":[{\"name\":\"x\",\"images\":["
                + "{\"url\":\"https://img.example/1.jpg\",\"focalX\":-20,\"focalY\":150}]}]}");
    assertThat(out).contains("\"focalX\":0");
    assertThat(out).contains("\"focalY\":100");
  }

  @Test
  void focalPointsDefaultTo50WhenMissing() {
    String out =
        ProductCardCarousel.normalize(
            "{\"items\":[{\"name\":\"x\","
                + "\"images\":[{\"url\":\"https://img.example/1.jpg\"}]}]}");
    assertThat(out).contains("\"focalX\":50");
    assertThat(out).contains("\"focalY\":50");
  }

  @Test
  void enforcesMaxImagesPerItemCap() {
    StringBuilder json = new StringBuilder("{\"items\":[{\"name\":\"x\",\"images\":[");
    for (int i = 0; i < ProductCardCarousel.MAX_IMAGES_PER_ITEM + 1; i++) {
      if (i > 0) json.append(',');
      json.append("{\"url\":\"https://img.example/").append(i).append(".jpg\"}");
    }
    json.append("]}]}");
    assertThatThrownBy(() -> ProductCardCarousel.normalize(json.toString()))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void requiresAtLeastOneItem() {
    assertThatThrownBy(() -> ProductCardCarousel.normalize("{\"items\":[]}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(
            () -> ProductCardCarousel.normalize("{\"title\":\"empty\",\"items\":[null]}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void itemNameRequired() {
    assertThatThrownBy(() -> ProductCardCarousel.normalize("{\"items\":[{\"price\":\"$10\"}]}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> ProductCardCarousel.normalize("{\"items\":[{\"name\":\"   \"}]}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void enforcesMaxItemsCap() {
    StringBuilder json = new StringBuilder("{\"items\":[");
    for (int i = 0; i < ProductCardCarousel.MAX_ITEMS + 1; i++) {
      if (i > 0) json.append(',');
      json.append("{\"name\":\"item ").append(i).append("\"}");
    }
    json.append("]}");
    assertThatThrownBy(() -> ProductCardCarousel.normalize(json.toString()))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void rejectsNonHttpImage() {
    assertThatThrownBy(
            () ->
                ProductCardCarousel.normalize(
                    "{\"items\":[{\"name\":\"x\","
                        + "\"images\":[{\"url\":\"file:///etc/passwd\"}]}]}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(
            () ->
                ProductCardCarousel.normalize(
                    "{\"items\":[{\"name\":\"x\","
                        + "\"images\":[{\"url\":\"javascript:alert(1)\"}]}]}"))
        .isInstanceOf(InvalidUsernameException.class);
    // Legacy field still validated.
    assertThatThrownBy(
            () ->
                ProductCardCarousel.normalize(
                    "{\"items\":[{\"name\":\"x\",\"image\":\"file:///etc/passwd\"}]}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void rejectsNonHttpCta() {
    assertThatThrownBy(
            () ->
                ProductCardCarousel.normalize(
                    "{\"items\":[{\"name\":\"x\",\"ctaUrl\":\"javascript:alert(1)\"}]}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void optionalFieldsBecomeNull() {
    String out =
        ProductCardCarousel.normalize(
            "{\"items\":[{\"name\":\"x\",\"price\":\"  \",\"description\":\"\"}]}");
    assertThat(out).contains("\"price\":null");
    assertThat(out).contains("\"description\":null");
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> ProductCardCarousel.normalize("not json"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> ProductCardCarousel.normalize(""))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void ignoresUnknownItemFields() {
    // Forward compat — a frontend rolled out ahead of a backend that doesn't know about a field
    // shouldn't 400 every write.
    String out =
        ProductCardCarousel.normalize("{\"items\":[{\"name\":\"x\",\"futureField\":\"hello\"}]}");
    assertThat(out).contains("\"name\":\"x\"");
  }

  @Test
  void originalPriceRoundTripsForStrikethroughDisplay() {
    String out =
        ProductCardCarousel.normalize(
            "{\"items\":[{\"name\":\"x\",\"price\":\"36,000원\",\"originalPrice\":\"45,000원\"}]}");
    assertThat(out).contains("\"price\":\"36,000원\"");
    assertThat(out).contains("\"originalPrice\":\"45,000원\"");
  }

  @Test
  void originalPriceTrimmedAndBlankBecomesNull() {
    String out =
        ProductCardCarousel.normalize("{\"items\":[{\"name\":\"x\",\"originalPrice\":\"   \"}]}");
    assertThat(out).contains("\"originalPrice\":null");
  }

  @Test
  void whitelistedBadgesPassThrough() {
    for (String badge : new String[] {"NEW", "BEST", "LIMITED", "SOLD_OUT"}) {
      String out =
          ProductCardCarousel.normalize(
              "{\"items\":[{\"name\":\"x\",\"badge\":\"" + badge + "\"}]}");
      assertThat(out).contains("\"badge\":\"" + badge + "\"");
    }
  }

  @Test
  void layoutDefaultsToCarouselWhenMissing() {
    String out = ProductCardCarousel.normalize("{\"items\":[{\"name\":\"x\"}]}");
    assertThat(out).contains("\"layout\":\"carousel\"");
  }

  @Test
  void layoutAcceptsBothWhitelistedValues() {
    for (String layout : new String[] {"carousel", "grid"}) {
      String out =
          ProductCardCarousel.normalize(
              "{\"layout\":\"" + layout + "\",\"items\":[{\"name\":\"x\"}]}");
      assertThat(out).contains("\"layout\":\"" + layout + "\"");
    }
  }

  @Test
  void unknownLayoutFallsBackToCarousel() {
    String out =
        ProductCardCarousel.normalize("{\"layout\":\"futureLayout\",\"items\":[{\"name\":\"x\"}]}");
    assertThat(out).contains("\"layout\":\"carousel\"");
  }

  @Test
  void unknownBadgeDropsToNullNotReject() {
    // Forward compat — a future badge id added by the frontend shouldn't 400 every write. We drop
    // it to null so old clients render no badge instead of an unknown chip.
    String out =
        ProductCardCarousel.normalize("{\"items\":[{\"name\":\"x\",\"badge\":\"futureBadge\"}]}");
    assertThat(out).contains("\"badge\":null");
  }
}
