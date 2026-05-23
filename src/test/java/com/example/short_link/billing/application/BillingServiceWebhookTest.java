package com.example.short_link.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.billing.domain.SubscriptionEvent;
import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BillingServiceWebhookTest {

  private UserRepository userRepository;
  private SubscriptionGateway subscriptions;
  private SimpleMeterRegistry meters;
  private BillingService service;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    subscriptions = Mockito.mock(SubscriptionGateway.class);
    meters = new SimpleMeterRegistry();
    service =
        new BillingService(
            userRepository,
            meters,
            new StripeProperties(
                "sk_test_dummy",
                "whsec_test",
                "price_pro",
                "https://success",
                "https://cancel",
                "https://portal"),
            subscriptions);
  }

  @Test
  void handleWebhookDispatchesCheckoutCompletedAndLinksCustomer() {
    SubscriptionEvent event =
        new SubscriptionEvent.CheckoutCompleted("checkout.session.completed", "42", "cus_test_1");
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-42"));
    Mockito.doReturn(42L).when(user).getId();
    when(userRepository.findById(42L)).thenReturn(Optional.of(user));

    service.handleWebhook("{}", "t=1,v1=abc");

    Mockito.verify(user).linkStripeCustomer("cus_test_1");
    assertThat(
            meters
                .counter("billing.webhook", "type", "checkout.session.completed", "result", "ok")
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void handleWebhookCheckoutCompletedSkipsWhenUserAlreadyHasCustomer() {
    SubscriptionEvent event =
        new SubscriptionEvent.CheckoutCompleted("checkout.session.completed", "99", "cus_test_new");
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-99"));
    Mockito.doReturn(99L).when(user).getId();
    user.linkStripeCustomer("cus_existing");
    when(userRepository.findById(99L)).thenReturn(Optional.of(user));

    service.handleWebhook("{}", "t=1,v1=abc");

    assertThat(user.getStripeCustomerId()).isEqualTo("cus_existing");
  }

  @Test
  void handleWebhookCheckoutCompletedIgnoresNonNumericClientRef() {
    SubscriptionEvent event =
        new SubscriptionEvent.CheckoutCompleted(
            "checkout.session.completed", "not-a-number", "cus_test");
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    service.handleWebhook("{}", "t=1,v1=abc");

    Mockito.verify(userRepository, Mockito.never()).findById(any());
  }

  @Test
  void handleWebhookCheckoutCompletedSkipsWhenClientRefMissing() {
    SubscriptionEvent event =
        new SubscriptionEvent.CheckoutCompleted("checkout.session.completed", null, "cus_test");
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    service.handleWebhook("{}", "t=1,v1=abc");

    Mockito.verify(userRepository, Mockito.never()).findById(any());
  }

  @Test
  void handleWebhookSubscriptionUpdatedSetsTierAndPeriod() {
    Instant end = Instant.ofEpochSecond(1700000000L);
    SubscriptionEvent event =
        new SubscriptionEvent.SubscriptionUpdated(
            "customer.subscription.updated", "cus_active", "sub_1", "active", end);
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-sub1"));
    user.linkStripeCustomer("cus_active");
    when(userRepository.findByStripeCustomerId("cus_active")).thenReturn(Optional.of(user));

    service.handleWebhook("{}", "t=1,v1=abc");

    Mockito.verify(user)
        .applySubscription(Mockito.eq("sub_1"), Mockito.eq("active"), Mockito.eq(end));
  }

  @Test
  void handleWebhookSubscriptionDeletedClearsSubscription() {
    SubscriptionEvent event =
        new SubscriptionEvent.SubscriptionDeleted("customer.subscription.deleted", "cus_del");
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-sub2"));
    user.linkStripeCustomer("cus_del");
    when(userRepository.findByStripeCustomerId("cus_del")).thenReturn(Optional.of(user));

    service.handleWebhook("{}", "t=1,v1=abc");

    Mockito.verify(user).clearSubscription();
  }

  @Test
  void handleWebhookSubscriptionSkipsWhenCustomerUnknown() {
    SubscriptionEvent event =
        new SubscriptionEvent.SubscriptionUpdated(
            "customer.subscription.updated", "cus_ghost", "sub_x", "active", null);
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);
    when(userRepository.findByStripeCustomerId("cus_ghost")).thenReturn(Optional.empty());

    service.handleWebhook("{}", "t=1,v1=abc");
    // No exception, no user write — log-only path
  }

  @Test
  void handleWebhookIgnoredTypeStillIncrementsOkMeter() {
    SubscriptionEvent event = new SubscriptionEvent.Ignored("invoice.paid");
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    service.handleWebhook("{}", "t=1,v1=abc");

    assertThat(meters.counter("billing.webhook", "type", "invoice.paid", "result", "ok").count())
        .isEqualTo(1.0);
  }
}
