package com.example.short_link.profile.domain.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.exception.InvalidUsernameException;
import org.junit.jupiter.api.Test;

class BookingTest {

  @Test
  void normalizesCalendlyUrl() {
    String out =
        Booking.normalize(
            "{\"url\":\"https://calendly.com/me/30min\",\"title\":\"30분 상담\",\"description\":\"무료\"}");
    assertThat(out).contains("calendly.com/me/30min");
    assertThat(out).contains("30분 상담");
  }

  @Test
  void normalizesCalDotComUrl() {
    String out = Booking.normalize("{\"url\":\"https://cal.com/me/intro\"}");
    assertThat(out).contains("cal.com/me/intro");
  }

  @Test
  void normalizesNaverBookingUrl() {
    String out = Booking.normalize("{\"url\":\"https://booking.naver.com/booking/13/123456\"}");
    assertThat(out).contains("booking.naver.com");
  }

  @Test
  void rejectsArbitraryHost() {
    assertThatThrownBy(() -> Booking.normalize("{\"url\":\"https://evil.example/phish\"}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void rejectsNonHttpScheme() {
    assertThatThrownBy(() -> Booking.normalize("{\"url\":\"javascript:alert(1)\"}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> Booking.normalize("{\"url\":\"ftp://calendly.com/abc\"}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void requiresUrl() {
    assertThatThrownBy(() -> Booking.normalize("{\"title\":\"예약\"}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> Booking.normalize("{\"url\":\"\"}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void trimsOptionalFields() {
    String out =
        Booking.normalize(
            "{\"url\":\"https://calendly.com/me\",\"title\":\"  hi  \",\"description\":\"\"}");
    assertThat(out).contains("\"title\":\"hi\"");
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> Booking.normalize("not json"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void ignoresUnknownFields() {
    // Forward compat — a frontend rolling out a new field (e.g. providerHint, calendarEmbed) before
    // the backend deploy shouldn't 400 every BOOKING save. Same hotfix pattern as ContactCard
    // logoFocalX/Y (PR #256): record without @JsonIgnoreProperties bounces every PATCH on default
    // FAIL_ON_UNKNOWN_PROPERTIES.
    String out =
        Booking.normalize(
            "{\"url\":\"https://calendly.com/me\",\"title\":\"hi\","
                + "\"futureField\":\"hello\",\"providerHint\":42}");
    assertThat(out).contains("\"title\":\"hi\"");
    assertThat(out).contains("calendly.com/me");
  }

  @Test
  void resolveDetectsProviderId() {
    assertThat(Booking.Provider.resolve("https://calendly.com/me"))
        .isPresent()
        .get()
        .extracting(Booking.Provider::id)
        .isEqualTo("calendly");
    assertThat(Booking.Provider.resolve("https://pf.kakao.com/_abc"))
        .isPresent()
        .get()
        .extracting(Booking.Provider::id)
        .isEqualTo("kakao_channel");
    assertThat(Booking.Provider.resolve("https://other.example")).isEmpty();
  }
}
