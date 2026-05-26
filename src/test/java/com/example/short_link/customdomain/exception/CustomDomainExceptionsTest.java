package com.example.short_link.customdomain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomDomainExceptionsTest {

  @Test
  void notVerifiedCarriesDomain() {
    CustomDomainException ex =
        new CustomDomainException(CustomDomainErrorCode.CUSTOM_DOMAIN_NOT_VERIFIED, "example.com");
    assertThat(ex).hasMessageContaining("example.com");
  }

  @Test
  void notFoundCarriesCode() {
    CustomDomainException ex =
        new CustomDomainException(CustomDomainErrorCode.CUSTOM_DOMAIN_NOT_FOUND);
    assertThat(ex.errorCode()).isEqualTo(CustomDomainErrorCode.CUSTOM_DOMAIN_NOT_FOUND);
  }
}
