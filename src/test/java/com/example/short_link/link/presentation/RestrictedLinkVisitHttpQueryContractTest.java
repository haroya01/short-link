package com.example.short_link.link.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class RestrictedLinkVisitHttpQueryContractTest extends LinkJourneyHttpSupport {
  private static final Map<String, String> CRAWLER =
      Map.of("User-Agent", "facebookexternalhit/1.1");

  @MockitoBean private GeoIpResolver geoIp;

  @BeforeEach
  void everyVisitorComesFromJapan() {
    when(geoIp.resolve(any())).thenReturn(new GeoLocation("JP", null, null));
  }

  @Test
  void aOnceOnlyLinkKeepsItsDestinationFromCrawlersAndPrefetchesUntilAPersonOpensIt()
      throws Exception {
    String code = createLink("restricted-create-once");
    String destination = "https://example.com/" + code;
    long linkId = linkId(code);
    request(
        "restricted-limit-views",
        owner,
        "PATCH",
        "/api/v1/links/" + code + "/protection",
        Map.of("maxViews", 1),
        200);

    for (String id : new String[] {"restricted-crawler-preview", "restricted-crawler-again"}) {
      String card = body(visit(id, "/" + code, CRAWLER, 200).body());
      assertThat(card)
          .contains("og:title")
          .doesNotContain(destination, "Fixture destination", "http-equiv=\"refresh\"");
    }
    var prefetch = visit("restricted-prefetch", "/" + code, Map.of("Sec-Purpose", "prefetch"), 403);
    assertThat(prefetch.headers().firstValue("Location")).isEmpty();
    assertThat(prefetch.headers().firstValue("Cache-Control")).contains("no-store");
    assertThat(number("SELECT view_count FROM link WHERE id = ?", linkId)).isZero();

    var opened = visit("restricted-person-visit", "/" + code, Map.of(), 302);
    assertThat(opened.headers().firstValue("Location")).contains(destination);
    assertThat(number("SELECT view_count FROM link WHERE id = ?", linkId)).isEqualTo(1);

    String spent = body(visit("restricted-crawler-after-limit", "/" + code, CRAWLER, 410).body());
    assertThat(spent).doesNotContain(destination);
  }

  @Test
  void aBlockedCountryNeitherSpendsTheLimitNorGetsAPreview() throws Exception {
    String code = createLink("restricted-create-blocked");
    String destination = "https://example.com/" + code;
    long linkId = linkId(code);
    String path = "/api/v1/links/" + code;
    request(
        "restricted-block-japan",
        owner,
        "PUT",
        path + "/blocked-countries",
        Map.of("codes", "jp"),
        200);
    request(
        "restricted-limit-blocked",
        owner,
        "PATCH",
        path + "/protection",
        Map.of("maxViews", 1),
        200);

    visit("restricted-blocked-person", "/" + code, Map.of(), 403);
    assertThat(number("SELECT view_count FROM link WHERE id = ?", linkId)).isZero();
    String crawled = body(visit("restricted-blocked-crawler", "/" + code, CRAWLER, 403).body());
    assertThat(crawled).doesNotContain(destination);
    assertThat(number("SELECT view_count FROM link WHERE id = ?", linkId)).isZero();
  }

  @Test
  void anUnrestrictedLinkStillPreviewsItsDestination() throws Exception {
    String code = createLink("restricted-create-open");

    String preview = body(visit("open-crawler-preview", "/" + code, CRAWLER, 200).body());

    assertThat(preview)
        .contains("http-equiv=\"refresh\"", "https://example.com/" + code, "Fixture destination");
  }

  private static String body(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
