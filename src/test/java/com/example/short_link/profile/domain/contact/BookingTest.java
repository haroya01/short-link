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
    // 프론트가 백엔드보다 먼저 새 필드를 보내도 저장이 400으로 막히면 안 된다.
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

  @Test
  void resolvesJapaneseReservationPages() {
    assertThat(Booking.Provider.resolve("https://beauty.hotpepper.jp/slnH000537382/"))
        .map(Booking.Provider::id)
        .contains("hotpepper_beauty");
    assertThat(Booking.Provider.resolve("https://www.hotpepper.jp/strJ000024328/yoyaku/"))
        .map(Booking.Provider::id)
        .contains("hotpepper_gourmet");
    assertThat(Booking.Provider.resolve("https://tabelog.com/tokyo/A1301/A130101/13001234/"))
        .map(Booking.Provider::id)
        .contains("tabelog");
    assertThat(Booking.Provider.resolve("https://s.tabelog.com/tokyo/A1301/A130101/13001234/"))
        .map(Booking.Provider::id)
        .contains("tabelog");
    assertThat(Booking.Provider.resolve("https://www.tablecheck.com/ja/shops/series/reserve"))
        .map(Booking.Provider::id)
        .contains("tablecheck");
    assertThat(Booking.Provider.resolve("https://tablecheck.com/ja/shops/series/reserve"))
        .map(Booking.Provider::id)
        .contains("tablecheck");
    assertThat(Booking.Provider.resolve("https://airrsv.net/netin/calendar"))
        .map(Booking.Provider::id)
        .contains("airreserve");
    assertThat(Booking.Provider.resolve("https://reserva.be/cocomano"))
        .map(Booking.Provider::id)
        .contains("reserva");
    assertThat(Booking.Provider.resolve("https://coubic.com/atsalon"))
        .map(Booking.Provider::id)
        .contains("stores_reserve");
    assertThat(Booking.Provider.resolve("https://lsgqavna01biggwfmvnb.stores.jp/reserve/atsalon"))
        .map(Booking.Provider::id)
        .contains("stores_reserve");
    assertThat(Booking.Provider.resolve("https://restaurant.ikyu.com/122619"))
        .map(Booking.Provider::id)
        .contains("ikyu_restaurant");
  }

  @Test
  void acceptsAStoresSubdomainOnlyOnItsReservationPages() {
    assertThat(Booking.Provider.resolve("https://lsgqavna01biggwfmvnb.stores.jp/items/123"))
        .isEmpty();
    assertThat(Booking.Provider.resolve("https://stores.jp/reserve/features")).isEmpty();
    assertThat(Booking.Provider.resolve("https://evilstores.jp/reserve/atsalon")).isEmpty();
    assertThat(Booking.Provider.resolve("https://shop.stores.jp.evil.example/reserve/atsalon"))
        .isEmpty();
    assertThat(Booking.Provider.resolve("https://evil.example/x.stores.jp/reserve/atsalon"))
        .isEmpty();
    assertThat(Booking.Provider.resolve("https://shop.stores.jp/RESERVE/atsalon")).isEmpty();
    assertThat(Booking.Provider.resolve("https://shop.stores.jp/%72eserve/atsalon")).isEmpty();
    assertThat(Booking.Provider.resolve("https://shop.stores.jp/reserve%2Fatsalon")).isEmpty();
    assertThat(Booking.Provider.resolve("https://shop.stores.jp/reserve/../items/1")).isEmpty();
    assertThat(Booking.Provider.resolve("https://shop.stores.jp/reserve/%2e%2e/items/1")).isEmpty();
    assertThat(Booking.Provider.resolve("https://shop.stores.jp/reserve")).isEmpty();
    assertThat(Booking.Provider.resolve("https://shop.stores.jp")).isEmpty();
    assertThatThrownBy(() -> Booking.normalize("{\"url\":\"https://shop.stores.jp/items/123\"}"))
        .isInstanceOf(ProfileException.class);
    assertThat(Booking.normalize("{\"url\":\"https://shop.stores.jp/reserve/atsalon\"}"))
        .contains("shop.stores.jp/reserve/atsalon");
  }

  @Test
  void rejectsLookalikesOfTheJapaneseHosts() {
    assertThat(Booking.Provider.resolve("https://tabelog.com.evil.example/x")).isEmpty();
    assertThat(Booking.Provider.resolve("https://beauty.hotpepper.jp.evil.example/x")).isEmpty();
    assertThat(Booking.Provider.resolve("https://evil-airrsv.net/x/calendar")).isEmpty();
    assertThat(Booking.Provider.resolve("https://reserva.be.evil.example/x")).isEmpty();
  }
}
