package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.CachedLink;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CachedLinkPickTest {

  @Test
  void noVariantsReturnsOriginal() {
    CachedLink link = new CachedLink(1L, "https://control", null, null, null, null);
    var picked = link.pick();
    assertThat(picked.url()).isEqualTo("https://control");
    assertThat(picked.destinationId()).isNull();
  }

  @Test
  void allDisabledFallsBackToOriginal() {
    CachedLink link =
        new CachedLink(
            1L,
            "https://control",
            null,
            null,
            null,
            null,
            List.of(
                new CachedLink.Variant(10L, "https://A", 50, false, null),
                new CachedLink.Variant(11L, "https://B", 50, false, null)));
    var picked = link.pick();
    assertThat(picked.url()).isEqualTo("https://control");
  }

  @Test
  void weightedDistributionConvergesToConfiguredRatio() {
    CachedLink link =
        new CachedLink(
            1L,
            "https://control",
            null,
            null,
            null,
            null,
            List.of(
                new CachedLink.Variant(10L, "https://A", 1, true, null),
                new CachedLink.Variant(11L, "https://B", 9, true, null)));
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
        new CachedLink(
            1L,
            "https://control",
            Instant.now().plusSeconds(60),
            null,
            null,
            null,
            List.of(new CachedLink.Variant(10L, "https://A", 0, true, null)));
    assertThat(link.pick().url()).isEqualTo("https://control");
  }

  @Test
  void countryMatchTakesPrecedenceOverAgnostic() {
    CachedLink link =
        new CachedLink(
            1L,
            "https://control",
            null,
            null,
            null,
            null,
            List.of(
                new CachedLink.Variant(10L, "https://kr", 50, true, "KR"),
                new CachedLink.Variant(11L, "https://any", 50, true, null)));
    // Korean visitor must always hit the KR variant.
    for (int i = 0; i < 200; i++) {
      assertThat(link.pick("KR").url()).isEqualTo("https://kr");
    }
    // Non-matching visitor falls through to agnostic.
    for (int i = 0; i < 200; i++) {
      assertThat(link.pick("US").url()).isEqualTo("https://any");
    }
  }

  @Test
  void allCountryTaggedAndNoneMatchFallsBackToOriginal() {
    CachedLink link =
        new CachedLink(
            1L,
            "https://control",
            null,
            null,
            null,
            null,
            List.of(
                new CachedLink.Variant(10L, "https://kr", 50, true, "KR"),
                new CachedLink.Variant(11L, "https://jp", 50, true, "JP")));
    assertThat(link.pick("US").url()).isEqualTo("https://control");
    assertThat(link.pick(null).url()).isEqualTo("https://control");
  }

  @Test
  void countryCodeMatchIsCaseInsensitive() {
    CachedLink link =
        new CachedLink(
            1L,
            "https://control",
            null,
            null,
            null,
            null,
            List.of(new CachedLink.Variant(10L, "https://kr", 50, true, "KR")));
    assertThat(link.pick("kr").url()).isEqualTo("https://kr");
  }
}
