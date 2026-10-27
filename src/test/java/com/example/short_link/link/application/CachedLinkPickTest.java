package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.dto.CachedLinkFixture;
import com.example.short_link.link.domain.LinkId;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CachedLinkPickTest {

  @Test
  void noVariantsReturnsOriginal() {
    CachedLink link = CachedLinkFixture.link(new LinkId(1L), "https://control").build();
    var picked = link.pick();
    assertThat(picked.url()).isEqualTo("https://control");
    assertThat(picked.destinationId()).isNull();
  }

  @Test
  void allDisabledFallsBackToOriginal() {
    CachedLink link =
        CachedLinkFixture.link(new LinkId(1L), "https://control")
            .variants(
                List.of(
                    CachedLinkFixture.variant(10L, "https://A", 50, false, null),
                    CachedLinkFixture.variant(11L, "https://B", 50, false, null)))
            .build();
    var picked = link.pick();
    assertThat(picked.url()).isEqualTo("https://control");
  }

  @Test
  void weightedDistributionConvergesToConfiguredRatio() {
    CachedLink link =
        CachedLinkFixture.link(new LinkId(1L), "https://control")
            .variants(
                List.of(
                    CachedLinkFixture.variant(10L, "https://A", 1, true, null),
                    CachedLinkFixture.variant(11L, "https://B", 9, true, null)))
            .build();
    Map<String, Integer> counts = new HashMap<>();
    int N = 20_000;
    for (int i = 0; i < N; i++) {
      String url = link.pick().url();
      counts.merge(url, 1, Integer::sum);
    }
    int a = counts.getOrDefault("https://A", 0);
    int b = counts.getOrDefault("https://B", 0);
    // 1:9 ratio with 20k samples — A should land within ~1% absolute of 10%
    double aRatio = (double) a / (a + b);
    assertThat(aRatio).isBetween(0.08, 0.12);
  }

  @Test
  void zeroSumWeightsFallBackToOriginal() {
    // weight clamps to >=1 normally, but defensive test: if all weights are 0 somehow
    CachedLink link =
        CachedLinkFixture.link(new LinkId(1L), "https://control")
            .expiresAt(Instant.now().plusSeconds(60))
            .variants(List.of(CachedLinkFixture.variant(10L, "https://A", 0, true, null)))
            .build();
    assertThat(link.pick().url()).isEqualTo("https://control");
  }

  @Test
  void countryMatchTakesPrecedenceOverAgnostic() {
    CachedLink link =
        CachedLinkFixture.link(new LinkId(1L), "https://control")
            .variants(
                List.of(
                    CachedLinkFixture.variant(10L, "https://kr", 50, true, "KR"),
                    CachedLinkFixture.variant(11L, "https://any", 50, true, null)))
            .build();
    for (int i = 0; i < 200; i++) {
      assertThat(link.pick("KR").url()).isEqualTo("https://kr");
    }
    for (int i = 0; i < 200; i++) {
      assertThat(link.pick("US").url()).isEqualTo("https://any");
    }
  }

  @Test
  void allCountryTaggedAndNoneMatchFallsBackToOriginal() {
    CachedLink link =
        CachedLinkFixture.link(new LinkId(1L), "https://control")
            .variants(
                List.of(
                    CachedLinkFixture.variant(10L, "https://kr", 50, true, "KR"),
                    CachedLinkFixture.variant(11L, "https://jp", 50, true, "JP")))
            .build();
    assertThat(link.pick("US").url()).isEqualTo("https://control");
    assertThat(link.pick(null).url()).isEqualTo("https://control");
  }

  @Test
  void countryCodeMatchIsCaseInsensitive() {
    CachedLink link =
        CachedLinkFixture.link(new LinkId(1L), "https://control")
            .variants(List.of(CachedLinkFixture.variant(10L, "https://kr", 50, true, "KR")))
            .build();
    assertThat(link.pick("kr").url()).isEqualTo("https://kr");
  }

  @Test
  void osMatchSelectsMoreSpecificVariant() {
    CachedLink link =
        CachedLinkFixture.link(new LinkId(1L), "https://control")
            .variants(
                List.of(
                    CachedLinkFixture.variant(10L, "https://generic", 50, true, null),
                    new CachedLink.Variant(11L, "https://ios-app", 50, true, null, null, "ios")))
            .build();
    assertThat(link.pick(null, "iOS", null).url()).isEqualTo("https://ios-app");
    assertThat(link.pick(null, "android", null).url()).isEqualTo("https://generic");
  }

  @Test
  void deviceClassMatchSelectsMobileVariant() {
    CachedLink link =
        CachedLinkFixture.link(new LinkId(1L), "https://control")
            .variants(
                List.of(
                    CachedLinkFixture.variant(10L, "https://web", 50, true, null),
                    new CachedLink.Variant(11L, "https://mobile", 50, true, null, "mobile", null)))
            .build();
    assertThat(link.pick(null, null, "Mobile").url()).isEqualTo("https://mobile");
    assertThat(link.pick(null, null, "desktop").url()).isEqualTo("https://web");
  }

  @Test
  void mostSpecificMultiPredicateVariantWins() {
    CachedLink link =
        CachedLinkFixture.link(new LinkId(1L), "https://control")
            .variants(
                List.of(
                    CachedLinkFixture.variant(10L, "https://kr-only", 50, true, "KR"),
                    new CachedLink.Variant(11L, "https://kr-ios", 50, true, "KR", null, "ios")))
            .build();
    assertThat(link.pick("KR", "ios", null).url()).isEqualTo("https://kr-ios");
    assertThat(link.pick("KR", "android", null).url()).isEqualTo("https://kr-only");
  }

  @Test
  void isExpiredHandlesNullAndPastAndFuture() {
    CachedLink past =
        CachedLinkFixture.link(new LinkId(1L), "https://x")
            .expiresAt(Instant.parse("2020-01-01T00:00:00Z"))
            .build();
    CachedLink future =
        CachedLinkFixture.link(new LinkId(1L), "https://x")
            .expiresAt(Instant.parse("2099-01-01T00:00:00Z"))
            .build();
    CachedLink noExpiry = CachedLinkFixture.link(new LinkId(1L), "https://x").build();

    Instant now = Instant.parse("2026-05-28T00:00:00Z");
    assertThat(past.isExpired(now)).isTrue();
    assertThat(future.isExpired(now)).isFalse();
    assertThat(noExpiry.isExpired(now)).isFalse();
  }

  @Test
  void isBlockedForRecognizesCommaSeparatedList() {
    CachedLink link =
        CachedLinkFixture.link(new LinkId(1L), "https://x").blockedCountries("KR, JP , US").build();

    assertThat(link.isBlockedFor("kr")).isTrue();
    assertThat(link.isBlockedFor("JP")).isTrue();
    assertThat(link.isBlockedFor("us")).isTrue();
    assertThat(link.isBlockedFor("FR")).isFalse();
    assertThat(link.isBlockedFor(null)).isFalse();
  }

  @Test
  void isBlockedForReturnsFalseWhenNoListConfigured() {
    CachedLink link = CachedLinkFixture.link(new LinkId(1L), "https://x").build();
    assertThat(link.isBlockedFor("KR")).isFalse();
  }
}
