package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import org.junit.jupiter.api.Test;

class LinkExceptionsTest {

  @Test
  void linkViewLimitExceededCarriesShortCode() {
    LinkException ex = new LinkException(LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED, "abc");
    assertThat(ex).hasMessageContaining("abc");
  }

  @Test
  void customDomainNotVerifiedCarriesDomain() {
    LinkException ex = new LinkException(LinkErrorCode.CUSTOM_DOMAIN_NOT_VERIFIED, "example.com");
    assertThat(ex).hasMessageContaining("example.com");
  }

  @Test
  void reservedShortCodeCarriesCode() {
    LinkException ex = new LinkException(LinkErrorCode.RESERVED_SHORT_CODE, "admin");
    assertThat(ex).hasMessageContaining("admin");
  }

  @Test
  void bulkImportTooLargeCarriesNumbers() {
    LinkException ex = new LinkException(LinkErrorCode.BULK_IMPORT_TOO_LARGE, 2000, 1000);
    assertThat(ex.properties().get("rows")).isEqualTo(2000);
    assertThat(ex.properties().get("limit")).isEqualTo(1000);
    assertThat(ex).hasMessageContaining("2000").hasMessageContaining("1000");
  }

  @Test
  void linkNotFoundCarriesCode() {
    LinkException ex = new LinkException(LinkErrorCode.LINK_NOT_FOUND, "abc");
    assertThat(ex).hasMessageContaining("abc");
  }

  @Test
  void linkExpiredCarriesCode() {
    LinkException ex = new LinkException(LinkErrorCode.LINK_EXPIRED, "abc");
    assertThat(ex).hasMessageContaining("abc");
  }

  @Test
  void linkNotOwnedCarriesCode() {
    LinkException ex = new LinkException(LinkErrorCode.LINK_NOT_OWNED, "abc");
    assertThat(ex).hasMessageContaining("abc");
  }
}
