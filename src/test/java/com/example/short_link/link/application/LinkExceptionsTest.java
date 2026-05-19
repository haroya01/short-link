package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkExceptionsTest {

  @Test
  void duplicateTagNameCarriesName() {
    DuplicateTagNameException ex = new DuplicateTagNameException("blog");
    assertThat(ex).hasMessageContaining("blog");
  }

  @Test
  void linkViewLimitExceededCarriesShortCode() {
    LinkViewLimitExceededException ex = new LinkViewLimitExceededException("abc");
    assertThat(ex).hasMessageContaining("abc");
  }

  @Test
  void customDomainNotVerifiedCarriesDomain() {
    CustomDomainNotVerifiedException ex = new CustomDomainNotVerifiedException("example.com");
    assertThat(ex).hasMessageContaining("example.com");
  }

  @Test
  void tooManyWebhooksCarriesLimit() {
    TooManyWebhooksException ex = new TooManyWebhooksException(5);
    assertThat(ex.getLimit()).isEqualTo(5);
    assertThat(ex).hasMessageContaining("5");
  }

  @Test
  void reservedShortCodeCarriesCode() {
    ReservedShortCodeException ex = new ReservedShortCodeException("admin");
    assertThat(ex).hasMessageContaining("admin");
  }

  @Test
  void bulkImportTooLargeCarriesNumbers() {
    BulkImportTooLargeException ex = new BulkImportTooLargeException(2000, 1000);
    assertThat(ex.getRows()).isEqualTo(2000);
    assertThat(ex.getLimit()).isEqualTo(1000);
    assertThat(ex).hasMessageContaining("2000").hasMessageContaining("1000");
  }

  @Test
  void linkNotFoundCarriesCode() {
    LinkNotFoundException ex = new LinkNotFoundException("abc");
    assertThat(ex).hasMessageContaining("abc");
  }

  @Test
  void linkExpiredCarriesCode() {
    LinkExpiredException ex = new LinkExpiredException("abc");
    assertThat(ex).hasMessageContaining("abc");
  }

  @Test
  void invalidWebhookUrlMessage() {
    InvalidWebhookUrlException ex = new InvalidWebhookUrlException();
    assertThat(ex).hasMessageContaining("webhook url");
  }

  @Test
  void linkNotOwnedCarriesCode() {
    LinkNotOwnedException ex = new LinkNotOwnedException("abc");
    assertThat(ex).hasMessageContaining("abc");
  }
}
