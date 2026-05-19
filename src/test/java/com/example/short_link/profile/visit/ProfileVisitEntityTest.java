package com.example.short_link.profile.visit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ProfileVisitEntityTest {

  @Test
  void builderPopulatesAllFields() {
    ProfileVisitEntity e =
        ProfileVisitEntity.builder()
            .profileUserId(7L)
            .referrer("https://t.co/x")
            .referrerHost("t.co")
            .userAgent("Mozilla/5.0")
            .clientIp("203.0.113.45")
            .utmSource("twitter")
            .utmMedium("social")
            .utmCampaign("launch")
            .utmTerm("brand")
            .utmContent("v2")
            .deviceClass("Desktop")
            .osName("macOS")
            .browserName("Chrome")
            .bot(false)
            .botName(null)
            .countryCode("KR")
            .regionName("Seoul")
            .cityName("Gangnam")
            .language("ko")
            .visitorHash("hash")
            .sourceChannel("x")
            .asn(15169)
            .asnOrg("Google LLC")
            .build();
    assertThat(e.getProfileUserId()).isEqualTo(7L);
    assertThat(e.getReferrer()).isEqualTo("https://t.co/x");
    assertThat(e.getReferrerHost()).isEqualTo("t.co");
    assertThat(e.getUserAgent()).isEqualTo("Mozilla/5.0");
    assertThat(e.getClientIp()).isEqualTo("203.0.113.45");
    assertThat(e.getUtmSource()).isEqualTo("twitter");
    assertThat(e.getUtmMedium()).isEqualTo("social");
    assertThat(e.getUtmCampaign()).isEqualTo("launch");
    assertThat(e.getUtmTerm()).isEqualTo("brand");
    assertThat(e.getUtmContent()).isEqualTo("v2");
    assertThat(e.getDeviceClass()).isEqualTo("Desktop");
    assertThat(e.getOsName()).isEqualTo("macOS");
    assertThat(e.getBrowserName()).isEqualTo("Chrome");
    assertThat(e.isBot()).isFalse();
    assertThat(e.getBotName()).isNull();
    assertThat(e.getCountryCode()).isEqualTo("KR");
    assertThat(e.getRegionName()).isEqualTo("Seoul");
    assertThat(e.getCityName()).isEqualTo("Gangnam");
    assertThat(e.getLanguage()).isEqualTo("ko");
    assertThat(e.getVisitorHash()).isEqualTo("hash");
    assertThat(e.getSourceChannel()).isEqualTo("x");
    assertThat(e.getAsn()).isEqualTo(15169);
    assertThat(e.getAsnOrg()).isEqualTo("Google LLC");
  }

  @Test
  void prePersistStampsVisitedAt() throws Exception {
    ProfileVisitEntity e = ProfileVisitEntity.builder().profileUserId(1L).bot(false).build();
    assertThat(e.getVisitedAt()).isNull();
    Method m = ProfileVisitEntity.class.getDeclaredMethod("prePersist");
    m.setAccessible(true);
    m.invoke(e);
    assertThat(e.getVisitedAt()).isNotNull();
  }
}
