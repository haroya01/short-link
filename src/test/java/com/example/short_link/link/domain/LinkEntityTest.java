package com.example.short_link.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.repository.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LinkEntityTest {

  @Test
  void shortConstructorLeavesUserAndExpiryNull() {
    LinkEntity link = new LinkEntity("https://x.com", "abc");
    assertThat(link.getOriginalUrl()).isEqualTo("https://x.com");
    assertThat(link.getShortCode().value()).isEqualTo("abc");
    assertThat(link.getUserId()).isNull();
    assertThat(link.getExpiresAt()).isNull();
    assertThat(link.getOgFetchStatus()).isEqualTo("PENDING");
    assertThat(link.getOgFetchAttempts()).isZero();
    assertThat(link.getViewCount()).isZero();
    assertThat(link.isStatsPublic()).isFalse();
    assertThat(link.isProfileHighlighted()).isFalse();
    assertThat(link.isOnProfile()).isFalse();
  }

  @Test
  void isExpiredOnlyTrueAfterExpiresAt() {
    Instant now = Instant.parse("2024-01-01T00:00:00Z");
    LinkEntity link = new LinkEntity("https://x", "abc", 1L, now.plusSeconds(60));
    assertThat(link.isExpired(now)).isFalse();
    assertThat(link.isExpired(now.plusSeconds(60))).isTrue();
    assertThat(link.isExpired(now.plusSeconds(120))).isTrue();
  }

  @Test
  void linkWithoutExpiryNeverExpires() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    assertThat(link.isExpired(Instant.now())).isFalse();
  }

  @Test
  void isOwnedByMatchesUserId() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    assertThat(link.isOwnedBy(7L)).isTrue();
    assertThat(link.isOwnedBy(8L)).isFalse();
    assertThat(link.isOwnedBy(null)).isFalse();
  }

  @Test
  void anonymousLinkOwnedByNoOne() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    assertThat(link.isOwnedBy(1L)).isFalse();
  }

  @Test
  void changeOriginalUrlResetsOgFields() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.applyOgMetadata("t", "d", "i", Instant.now());
    link.changeOriginalUrl("https://y");
    assertThat(link.getOriginalUrl()).isEqualTo("https://y");
    assertThat(link.getOgTitle()).isNull();
    assertThat(link.getOgDescription()).isNull();
    assertThat(link.getOgImage()).isNull();
    assertThat(link.getOgFetchedAt()).isNull();
    assertThat(link.getOgFetchStatus()).isEqualTo("PENDING");
  }

  @Test
  void changeExpiresAt() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    Instant when = Instant.parse("2030-01-01T00:00:00Z");
    link.changeExpiresAt(when);
    assertThat(link.getExpiresAt()).isEqualTo(when);
  }

  @Test
  void applyOgMetadataIncrementsAttempts() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.applyOgMetadata("t", "d", "i", Instant.now());
    assertThat(link.getOgFetchStatus()).isEqualTo("OK");
    assertThat(link.getOgFetchAttempts()).isEqualTo(1);
    link.applyOgMetadata("t2", "d2", "i2", Instant.now());
    assertThat(link.getOgFetchAttempts()).isEqualTo(2);
  }

  @Test
  void markOgFetchFailedUsesRetryStatus() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.markOgFetchFailed(Instant.now(), true);
    assertThat(link.getOgFetchStatus()).isEqualTo("RETRYABLE");
    assertThat(link.getOgFetchAttempts()).isEqualTo(1);
    link.markOgFetchFailed(Instant.now(), false);
    assertThat(link.getOgFetchStatus()).isEqualTo("ERROR");
    assertThat(link.getOgFetchAttempts()).isEqualTo(2);
  }

  @Test
  void changeStatsVisibility() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.changeStatsVisibility(true);
    assertThat(link.isStatsPublic()).isTrue();
  }

  @Test
  void profileOrderAndHighlighted() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.setProfileOrder(5);
    assertThat(link.getProfileOrder()).isEqualTo(5);
    assertThat(link.isOnProfile()).isTrue();
    link.setProfileOrder(null);
    assertThat(link.isOnProfile()).isFalse();
    link.setProfileHighlighted(true);
    assertThat(link.isProfileHighlighted()).isTrue();
  }

  @Test
  void noteTrimmedAndCappedAt280() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.updateNote("  hello  ");
    assertThat(link.getNote()).isEqualTo("hello");
    String big = "x".repeat(400);
    link.updateNote(big);
    assertThat(link.getNote()).hasSize(280);
    link.updateNote("");
    assertThat(link.getNote()).isNull();
    link.updateNote(null);
    assertThat(link.getNote()).isNull();
  }

  @Test
  void expiredMessageTrimmedAndCappedAt500() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.updateExpiredMessage("  hi  ");
    assertThat(link.getExpiredMessage()).isEqualTo("hi");
    String big = "y".repeat(700);
    link.updateExpiredMessage(big);
    assertThat(link.getExpiredMessage()).hasSize(500);
    link.updateExpiredMessage("   ");
    assertThat(link.getExpiredMessage()).isNull();
    link.updateExpiredMessage(null);
    assertThat(link.getExpiredMessage()).isNull();
  }

  @Test
  void blockedCountriesNormalizedToUppercaseUnique() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.setBlockedCountries("kr, jp , us , kr");
    assertThat(link.getBlockedCountries()).isEqualTo("KR,JP,US");
  }

  @Test
  void blockedCountriesIgnoresInvalidCodes() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.setBlockedCountries("kor, j, fr");
    assertThat(link.getBlockedCountries()).isEqualTo("FR");
  }

  @Test
  void blockedCountriesBlankClears() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.setBlockedCountries("KR");
    link.setBlockedCountries("  ");
    assertThat(link.getBlockedCountries()).isNull();
    link.setBlockedCountries(null);
    assertThat(link.getBlockedCountries()).isNull();
  }

  @Test
  void isCountryBlockedCaseInsensitive() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.setBlockedCountries("KR,JP");
    assertThat(link.isCountryBlocked("kr")).isTrue();
    assertThat(link.isCountryBlocked("US")).isFalse();
    assertThat(link.isCountryBlocked(null)).isFalse();
  }

  @Test
  void isCountryBlockedFalseWhenBlocklistEmpty() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    assertThat(link.isCountryBlocked("KR")).isFalse();
  }

  @Test
  void claimMovesOwnershipAndClearsExpiry() {
    LinkEntity link = new LinkEntity("https://x", "abc", null, Instant.now().plusSeconds(60));
    link.setClaimToken("tok");
    link.claim(99L);
    assertThat(link.getUserId()).isEqualTo(99L);
    assertThat(link.getClaimToken()).isNull();
    assertThat(link.getExpiresAt()).isNull();
  }

  @Test
  void passwordHashHelpers() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    assertThat(link.hasPassword()).isFalse();
    link.setPasswordHash("h");
    assertThat(link.hasPassword()).isTrue();
    link.setPasswordHash("   ");
    assertThat(link.hasPassword()).isFalse();
    link.setPasswordHash("h");
    link.setPasswordHash(null);
    assertThat(link.hasPassword()).isFalse();
  }

  @Test
  void viewLimitHelpers() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    assertThat(link.isViewLimitReached()).isFalse();
    link.setMaxViews(3);
    assertThat(link.isViewLimitReached()).isFalse();
    link.incrementViewCount();
    link.incrementViewCount();
    link.incrementViewCount();
    assertThat(link.isViewLimitReached()).isTrue();
  }

  @Test
  void ogOverrideTrimmedAndBlankBecomesNull() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.changeOgOverride(" T ", " D ", " I ");
    assertThat(link.getOgTitleOverride()).isEqualTo("T");
    assertThat(link.getOgDescriptionOverride()).isEqualTo("D");
    assertThat(link.getOgImageOverride()).isEqualTo("I");
    link.changeOgOverride("", "  ", null);
    assertThat(link.getOgTitleOverride()).isNull();
    assertThat(link.getOgDescriptionOverride()).isNull();
    assertThat(link.getOgImageOverride()).isNull();
  }

  @Test
  void effectiveOgPrefersOverrideOverScraped() {
    LinkEntity link = new LinkEntity("https://x", "abc");
    link.applyOgMetadata("scrapedT", "scrapedD", "scrapedI", Instant.now());
    assertThat(link.getEffectiveOgTitle()).isEqualTo("scrapedT");
    assertThat(link.getEffectiveOgDescription()).isEqualTo("scrapedD");
    assertThat(link.getEffectiveOgImage()).isEqualTo("scrapedI");
    link.changeOgOverride("ovT", "ovD", "ovI");
    assertThat(link.getEffectiveOgTitle()).isEqualTo("ovT");
    assertThat(link.getEffectiveOgDescription()).isEqualTo("ovD");
    assertThat(link.getEffectiveOgImage()).isEqualTo("ovI");
  }
}
