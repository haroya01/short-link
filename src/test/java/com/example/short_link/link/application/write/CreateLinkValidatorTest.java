package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.security.BlockedDomainChecker;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.safety.application.UrlSafetyChecker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class CreateLinkValidatorTest {

  private final CreateLinkValidator validator = validator("https://kurl.me");

  @Test
  void rejectsUrlOnOwnShortLinkHost() {
    assertSelfReferenceRejected("https://kurl.me/abc123");
    assertSelfReferenceRejected("http://kurl.me/abc123");
    assertSelfReferenceRejected("https://KURL.ME/abc123");
    assertSelfReferenceRejected("https://kurl.me/abc123?utm_source=x#frag");
  }

  @Test
  void rejectsWwwVariantOfOwnHost() {
    assertSelfReferenceRejected("https://www.kurl.me/abc123");
  }

  @Test
  void allowsContentSubdomains() {
    assertThatCode(() -> validator.validateUrl("https://blog.kurl.me/haroya/some-post"))
        .doesNotThrowAnyException();
    assertThatCode(() -> validator.validateUrl("https://haroya.kurl.me/another-post"))
        .doesNotThrowAnyException();
  }

  @Test
  void allowsForeignHostsIncludingLookalikes() {
    assertThatCode(() -> validator.validateUrl("https://example.com/kurl.me"))
        .doesNotThrowAnyException();
    assertThatCode(() -> validator.validateUrl("https://kurl.me.evil.com/abc"))
        .doesNotThrowAnyException();
  }

  @Test
  void matchesApexWhenBaseUrlCarriesWww() {
    CreateLinkValidator wwwBased = validator("https://www.kurl.me");

    assertThatThrownBy(() -> wwwBased.validateUrl("https://kurl.me/abc123"))
        .isInstanceOfSatisfying(
            LinkException.class,
            e -> assertThat(e.errorCode()).isEqualTo(LinkErrorCode.SELF_REFERENCING_URL));
  }

  @Test
  void skipsSelfCheckForUnparseableUrl() {
    // URL 형식 자체는 상류(요청 DTO·CSV 행)에서 걸러진다 — 여기서는 조용히 비켜선다.
    assertThatCode(() -> validator.validateUrl("https://exa mple.com/x"))
        .doesNotThrowAnyException();
  }

  private void assertSelfReferenceRejected(String url) {
    assertThatThrownBy(() -> validator.validateUrl(url))
        .isInstanceOfSatisfying(
            LinkException.class,
            e -> assertThat(e.errorCode()).isEqualTo(LinkErrorCode.SELF_REFERENCING_URL));
  }

  private static CreateLinkValidator validator(String baseUrl) {
    BlockedDomainChecker blockedDomainChecker = mock(BlockedDomainChecker.class);
    when(blockedDomainChecker.isBlocked(any())).thenReturn(false);
    UrlSafetyChecker urlSafetyChecker = mock(UrlSafetyChecker.class);
    when(urlSafetyChecker.isSafe(any())).thenReturn(true);
    return new CreateLinkValidator(
        blockedDomainChecker, urlSafetyChecker, new SimpleMeterRegistry(), baseUrl);
  }
}
