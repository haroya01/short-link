package com.example.short_link.profile.domain.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.exception.InvalidUsernameException;
import org.junit.jupiter.api.Test;

class PlaceTest {

  @Test
  void normalizesValidPayload() {
    String out =
        Place.normalize(
            "{\"name\":\"  누데이크 하우스 도산  \","
                + "\"address\":\"서울 강남구 압구정로46길 50\","
                + "\"lat\":37.5237,\"lng\":127.0386,"
                + "\"placeId\":\"ChIJxxxxxxxxxxxxxxxxxxxxxx\","
                + "\"phone\":\"02-1234-5678\","
                + "\"coverUrl\":\"https://img.example/store.jpg\","
                + "\"category\":\"cafe\","
                + "\"hoursText\":\"매일 11:00-22:00, 화요일 휴무\"}");
    assertThat(out).contains("\"name\":\"누데이크 하우스 도산\"");
    assertThat(out).contains("\"address\":\"서울 강남구 압구정로46길 50\"");
    assertThat(out).contains("\"lat\":37.5237");
    assertThat(out).contains("\"lng\":127.0386");
    assertThat(out).contains("\"category\":\"cafe\"");
  }

  @Test
  void nameRequired() {
    assertThatThrownBy(() -> Place.normalize("{\"address\":\"서울\",\"lat\":37,\"lng\":127}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(
            () -> Place.normalize("{\"name\":\"   \",\"address\":\"서울\",\"lat\":37,\"lng\":127}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void addressRequired() {
    assertThatThrownBy(() -> Place.normalize("{\"name\":\"x\",\"lat\":37,\"lng\":127}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void latLngRequired() {
    assertThatThrownBy(() -> Place.normalize("{\"name\":\"x\",\"address\":\"서울\"}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> Place.normalize("{\"name\":\"x\",\"address\":\"서울\",\"lat\":37}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void latLngOutOfRange() {
    assertThatThrownBy(
            () -> Place.normalize("{\"name\":\"x\",\"address\":\"서울\",\"lat\":91,\"lng\":127}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(
            () -> Place.normalize("{\"name\":\"x\",\"address\":\"서울\",\"lat\":37,\"lng\":181}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(
            () -> Place.normalize("{\"name\":\"x\",\"address\":\"서울\",\"lat\":-91,\"lng\":127}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void unknownCategoryDroppedNotRejected() {
    // Forward compat: a frontend rolled out with a new category before the backend doesn't 400.
    // The category just renders empty until the next backend deploy.
    String out =
        Place.normalize(
            "{\"name\":\"x\",\"address\":\"서울\",\"lat\":37,\"lng\":127,"
                + "\"category\":\"futurecategorythatdoesntexist\"}");
    assertThat(out).contains("\"category\":null");
  }

  @Test
  void categoryWhitelistAccepted() {
    for (String cat :
        new String[] {
          "cafe", "bakery", "restaurant", "retail", "studio", "gallery", "popup", "space"
        }) {
      String out =
          Place.normalize(
              "{\"name\":\"x\",\"address\":\"서울\",\"lat\":37,\"lng\":127,\"category\":\""
                  + cat
                  + "\"}");
      assertThat(out).contains("\"category\":\"" + cat + "\"");
    }
  }

  @Test
  void rejectsNonHttpCoverUrl() {
    assertThatThrownBy(
            () ->
                Place.normalize(
                    "{\"name\":\"x\",\"address\":\"서울\",\"lat\":37,\"lng\":127,"
                        + "\"coverUrl\":\"javascript:alert(1)\"}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(
            () ->
                Place.normalize(
                    "{\"name\":\"x\",\"address\":\"서울\",\"lat\":37,\"lng\":127,"
                        + "\"coverUrl\":\"file:///etc/passwd\"}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void optionalFieldsBecomeNull() {
    String out =
        Place.normalize(
            "{\"name\":\"x\",\"address\":\"서울\",\"lat\":37,\"lng\":127,"
                + "\"phone\":\"  \",\"hoursText\":\"\"}");
    assertThat(out).contains("\"phone\":null");
    assertThat(out).contains("\"hoursText\":null");
  }

  @Test
  void ignoresUnknownFields() {
    // Forward compat — frontend may send v2 fields (hoursJson, mapStyle) before backend knows.
    String out =
        Place.normalize(
            "{\"name\":\"x\",\"address\":\"서울\",\"lat\":37,\"lng\":127,"
                + "\"hoursJson\":{\"mon\":[[\"10:00\",\"22:00\"]]},\"mapStyle\":\"dark\"}");
    assertThat(out).contains("\"name\":\"x\"");
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> Place.normalize("not json"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> Place.normalize("")).isInstanceOf(InvalidUsernameException.class);
  }
}
