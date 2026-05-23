package com.example.short_link.billing.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.billing.application.StripeProperties;
import org.junit.jupiter.api.Test;

class BillingQueryServiceTest {

  @Test
  void returnsTrueWhenStripePropsConfigured() {
    StripeProperties configured =
        new StripeProperties(
            "sk_test", "whsec", "price_pro", "https://s", "https://c", "https://p");
    assertThat(new BillingQueryService(configured).isBillingConfigured()).isTrue();
  }

  @Test
  void returnsFalseWhenApiKeyBlank() {
    StripeProperties noKey =
        new StripeProperties("", "whsec", "price_pro", "https://s", "https://c", "https://p");
    assertThat(new BillingQueryService(noKey).isBillingConfigured()).isFalse();
  }

  @Test
  void returnsFalseWhenPriceBlank() {
    StripeProperties noPrice =
        new StripeProperties("sk_test", "whsec", "", "https://s", "https://c", "https://p");
    assertThat(new BillingQueryService(noPrice).isBillingConfigured()).isFalse();
  }
}
