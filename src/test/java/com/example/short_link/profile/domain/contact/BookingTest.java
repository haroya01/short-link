package com.example.short_link.profile.domain.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.exception.ProfileException;
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
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsNonHttpScheme() {
    assertThatThrownBy(() -> Booking.normalize("{\"url\":\"javascript:alert(1)\"}"))
        .isInstanceOf(ProfileException.class);
    assertThatThrownBy(() -> Booking.normalize("{\"url\":\"ftp://calendly.com/abc\"}"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void requiresUrl() {
    assertThatThrownBy(() -> Booking.normalize("{\"title\":\"예약\"}"))
        .isInstanceOf(ProfileException.class);
    assertThatThrownBy(() -> Booking.normalize("{\"url\":\"\"}"))
        .isInstanceOf(ProfileException.class);
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
    assertThatThrownBy(() -> Booking.normalize("not json")).isInstanceOf(ProfileException.class);
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

  @Test
  void resolveHandlesNullAndBlank() {
    assertThat(Booking.Provider.resolve(null)).isEmpty();
    assertThat(Booking.Provider.resolve("")).isEmpty();
    assertThat(Booking.Provider.resolve("   ")).isEmpty();
  }

  @Test
  void resolveHandlesSchemelessAndHostlessUrl() {
    assertThat(Booking.Provider.resolve("calendly.com/me")).isEmpty();
    assertThat(Booking.Provider.resolve("https:///path")).isEmpty();
  }

  @Test
  void resolveHandlesMalformedUri() {
    assertThat(Booking.Provider.resolve("ht!tp://bad uri")).isEmpty();
  }

  @Test
  void resolveDetectsAllRemainingProviders() {
    assertThat(Booking.Provider.resolve("https://calendar.app.google/abc"))
        .map(Booking.Provider::id)
        .contains("google_calendar");
    assertThat(Booking.Provider.resolve("https://outlook.office.com/abc"))
        .map(Booking.Provider::id)
        .contains("microsoft_bookings");
    assertThat(Booking.Provider.resolve("https://tidycal.com/x"))
        .map(Booking.Provider::id)
        .contains("tidycal");
    assertThat(Booking.Provider.resolve("https://app.acuityscheduling.com/x"))
        .map(Booking.Provider::id)
        .contains("acuity");
    assertThat(Booking.Provider.resolve("https://catchtable.co.kr/x"))
        .map(Booking.Provider::id)
        .contains("catchtable");
  }
}
