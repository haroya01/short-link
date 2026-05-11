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
                + "{\"name\":\"  딸기 생크림  \",\"image\":\"https://img.example/1.jpg\","
                + "\"price\":\"45,000원\",\"description\":\"1호, 24h 전 예약\","
                + "\"ctaLabel\":\"주문\",\"ctaUrl\":\"https://pf.kakao.com/_abc\"}]}");
    assertThat(out).contains("\"title\":\"이번 주 케이크\"");
    assertThat(out).contains("\"name\":\"딸기 생크림\"");
    assertThat(out).contains("\"image\":\"https://img.example/1.jpg\"");
    assertThat(out).contains("\"ctaUrl\":\"https://pf.kakao.com/_abc\"");
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
                    "{\"items\":[{\"name\":\"x\",\"image\":\"file:///etc/passwd\"}]}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(
            () ->
                ProductCardCarousel.normalize(
                    "{\"items\":[{\"name\":\"x\",\"image\":\"javascript:alert(1)\"}]}"))
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
}
