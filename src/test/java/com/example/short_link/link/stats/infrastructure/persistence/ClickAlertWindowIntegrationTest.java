package com.example.short_link.link.stats.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickAlertReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClickAlertWindowIntegrationTest {

  @Autowired private ClickAlertReadRepository alerts;
  @Autowired private ClickEventRepository clicks;
  @Autowired private LinkRepository links;

  private final Instant now = Instant.now();
  private final Instant anHourAgo = now.minus(1, ChronoUnit.HOURS);
  private Long linkId;

  @BeforeEach
  void aClickAnHourAgo() {
    linkId = links.save(new LinkEntity("https://example.com/alert", "alrtwin1")).getId();
    clicks.save(
        ClickEventEntity.builder()
            .linkId(new com.example.short_link.link.domain.LinkId(linkId))
            .clickedAt(anHourAgo)
            .bot(false)
            .sourceChannel("qr")
            .countryCode("KR")
            .deviceClass("mobile")
            .referrerHost("x.com")
            .build());
  }

  @Test
  void aWindowAroundTheClickFindsItsChannelCountryDeviceAndHour() {
    Instant from = now.minus(2, ChronoUnit.HOURS);
    Instant to = now.minus(30, ChronoUnit.MINUTES);

    assertThat(alerts.findTopChannelByLinkIdAndRange(linkId, from, to))
        .hasValueSatisfying(r -> assertThat(r.getSource()).isEqualTo("qr"));
    assertThat(alerts.findTopCountryByLinkIdAndRange(linkId, from, to))
        .hasValueSatisfying(r -> assertThat(r.getCountry()).isEqualTo("KR"));
    assertThat(alerts.findTopDeviceByLinkIdAndRange(linkId, from, to))
        .hasValueSatisfying(r -> assertThat(r.getDevice()).isEqualTo("mobile"));
    assertThat(alerts.findPeakHourByLinkIdAndRange(linkId, from, to, "+09:00"))
        .hasValueSatisfying(
            r ->
                assertThat(r.getHour())
                    .isEqualTo(anHourAgo.atZone(ZoneId.of("Asia/Seoul")).getHour()));
  }

  @Test
  void aWindowThatEndsBeforeTheClickMissesIt() {
    Instant from = now.minus(3, ChronoUnit.HOURS);
    Instant to = now.minus(2, ChronoUnit.HOURS);

    assertThat(alerts.findTopCountryByLinkIdAndRange(linkId, from, to)).isEmpty();
  }

  @Test
  void theSpikeLookbackFindsTheReferrerOfAClickInsideIt() {
    assertThat(alerts.findTopReferrerHostByLinkIdSince(linkId, now.minus(90, ChronoUnit.MINUTES)))
        .hasValueSatisfying(r -> assertThat(r.getHost()).isEqualTo("x.com"));
    assertThat(alerts.findTopReferrerHostByLinkIdSince(linkId, now.minus(30, ChronoUnit.MINUTES)))
        .isEmpty();
  }
}
