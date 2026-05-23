package com.example.short_link.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private SubscriptionGateway subscriptions;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
  }

  private BillingService configured() {
    return new BillingService(
        userRepository,
        meterRegistry,
        new StripeProperties(
            "sk_test_x",
            "whsec_x",
            "price_pro",
            "https://app/success",
            "https://app/cancel",
            "https://app/portal"),
        subscriptions);
  }

  private BillingService unconfigured() {
    return new BillingService(
        userRepository,
        meterRegistry,
        new StripeProperties("", "", "", "https://app", "https://app", "https://app"),
        subscriptions);
  }

  @Test
  void isConfiguredRequiresApiKeyAndPrice() {
    assertThat(configured().isConfigured()).isTrue();
    assertThat(unconfigured().isConfigured()).isFalse();
    BillingService missingPrice =
        new BillingService(
            userRepository,
            meterRegistry,
            new StripeProperties(
                "sk_test_x", "whsec_x", "", "https://app", "https://app", "https://app"),
            subscriptions);
    assertThat(missingPrice.isConfigured()).isFalse();
  }

  @Test
  void checkoutRejectedWhenUnconfigured() {
    assertThatThrownBy(() -> unconfigured().createCheckoutSession(1L))
        .isInstanceOf(BillingNotConfiguredException.class);
  }

  @Test
  void portalRejectedWhenUnconfigured() {
    assertThatThrownBy(() -> unconfigured().createPortalSession(1L))
        .isInstanceOf(BillingNotConfiguredException.class);
  }

  @Test
  void checkoutThrowsWhenUserMissing() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> configured().createCheckoutSession(1L))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void portalThrowsWhenUserMissing() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> configured().createPortalSession(1L))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void portalThrowsWhenUserHasNoStripeCustomer() {
    UserEntity u = new UserEntity("u@x", "google", "g-1");
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));
    assertThatThrownBy(() -> configured().createPortalSession(1L))
        .isInstanceOf(BillingNotEnrolledException.class);
  }

  @Test
  void webhookInvalidSignaturePropagatesAndIncrementsMeter() {
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString()))
        .thenThrow(new InvalidWebhookSignatureException());
    assertThatThrownBy(() -> configured().handleWebhook("{}", "bogus"))
        .isInstanceOf(InvalidWebhookSignatureException.class);
    assertThat(meterRegistry.counter("billing.webhook", "result", "invalid_signature").count())
        .isEqualTo(1.0);
  }

  @Test
  void exceptionsCarryMessages() {
    assertThat(new BillingNotConfiguredException()).hasMessage("billing not configured");
    assertThat(new BillingNotEnrolledException()).hasMessage("user has no Stripe customer yet");
    assertThat(new InvalidWebhookSignatureException())
        .hasMessage("invalid Stripe webhook signature");
  }
}
