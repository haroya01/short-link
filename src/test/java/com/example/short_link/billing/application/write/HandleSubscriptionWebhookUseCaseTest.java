package com.example.short_link.billing.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.billing.application.InvalidWebhookSignatureException;
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

class HandleSubscriptionWebhookUseCaseTest {

  private UserRepository userRepository;
  private SubscriptionGateway subscriptions;
  private SimpleMeterRegistry meters;
  private HandleSubscriptionWebhookUseCase useCase;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    subscriptions = Mockito.mock(SubscriptionGateway.class);
    meters = new SimpleMeterRegistry();
    useCase = new HandleSubscriptionWebhookUseCase(subscriptions, userRepository, meters);
  }

  @Test
  void commandRejectsNullPayload() {
    assertThatThrownBy(() -> new SubscriptionWebhookCommand(null, "sig"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void commandRejectsNullSignature() {
    assertThatThrownBy(() -> new SubscriptionWebhookCommand("{}", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidSignaturePropagatesAndIncrementsMeter() {
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString()))
        .thenThrow(new InvalidWebhookSignatureException());
    assertThatThrownBy(() -> useCase.execute(new SubscriptionWebhookCommand("{}", "bogus")))
        .isInstanceOf(InvalidWebhookSignatureException.class);
    assertThat(meters.counter("billing.webhook", "result", "invalid_signature").count())
        .isEqualTo(1.0);
  }

  @Test
  void checkoutCompletedLinksCustomer() {
    SubscriptionEvent event =
        new SubscriptionEvent.CheckoutCompleted("checkout.session.completed", "42", "cus_test_1");
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-42"));
    Mockito.doReturn(42L).when(user).getId();
    when(userRepository.findById(42L)).thenReturn(Optional.of(user));

    useCase.execute(new SubscriptionWebhookCommand("{}", "t=1,v1=abc"));

    Mockito.verify(user).linkStripeCustomer("cus_test_1");
    assertThat(
            meters
                .counter("billing.webhook", "type", "checkout.session.completed", "result", "ok")
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void checkoutCompletedSkipsWhenUserAlreadyHasCustomer() {
    SubscriptionEvent event =
        new SubscriptionEvent.CheckoutCompleted("checkout.session.completed", "99", "cus_test_new");
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-99"));
    Mockito.doReturn(99L).when(user).getId();
    user.linkStripeCustomer("cus_existing");
    when(userRepository.findById(99L)).thenReturn(Optional.of(user));

    useCase.execute(new SubscriptionWebhookCommand("{}", "t=1,v1=abc"));

    assertThat(user.getStripeCustomerId()).isEqualTo("cus_existing");
  }

  @Test
  void checkoutCompletedIgnoresNonNumericClientRef() {
    SubscriptionEvent event =
        new SubscriptionEvent.CheckoutCompleted(
            "checkout.session.completed", "not-a-number", "cus_test");
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    useCase.execute(new SubscriptionWebhookCommand("{}", "t=1,v1=abc"));

    Mockito.verify(userRepository, Mockito.never()).findById(any());
  }

  @Test
  void checkoutCompletedSkipsWhenClientRefMissing() {
    SubscriptionEvent event =
        new SubscriptionEvent.CheckoutCompleted("checkout.session.completed", null, "cus_test");
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    useCase.execute(new SubscriptionWebhookCommand("{}", "t=1,v1=abc"));

    Mockito.verify(userRepository, Mockito.never()).findById(any());
  }

  @Test
  void subscriptionUpdatedAppliesToUser() {
    Instant end = Instant.ofEpochSecond(1700000000L);
    SubscriptionEvent event =
        new SubscriptionEvent.SubscriptionUpdated(
            "customer.subscription.updated", "cus_active", "sub_1", "active", end);
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-sub1"));
    user.linkStripeCustomer("cus_active");
    when(userRepository.findByStripeCustomerId("cus_active")).thenReturn(Optional.of(user));

    useCase.execute(new SubscriptionWebhookCommand("{}", "t=1,v1=abc"));

    Mockito.verify(user)
        .applySubscription(Mockito.eq("sub_1"), Mockito.eq("active"), Mockito.eq(end));
  }

  @Test
  void subscriptionDeletedClearsSubscription() {
    SubscriptionEvent event =
        new SubscriptionEvent.SubscriptionDeleted("customer.subscription.deleted", "cus_del");
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-sub2"));
    user.linkStripeCustomer("cus_del");
    when(userRepository.findByStripeCustomerId("cus_del")).thenReturn(Optional.of(user));

    useCase.execute(new SubscriptionWebhookCommand("{}", "t=1,v1=abc"));

    Mockito.verify(user).clearSubscription();
  }

  @Test
  void subscriptionSkipsWhenCustomerUnknown() {
    SubscriptionEvent event =
        new SubscriptionEvent.SubscriptionUpdated(
            "customer.subscription.updated", "cus_ghost", "sub_x", "active", null);
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);
    when(userRepository.findByStripeCustomerId("cus_ghost")).thenReturn(Optional.empty());

    useCase.execute(new SubscriptionWebhookCommand("{}", "t=1,v1=abc"));
    // No exception, no user write — log-only path
  }

  @Test
  void ignoredTypeStillIncrementsOkMeter() {
    SubscriptionEvent event = new SubscriptionEvent.Ignored("invoice.paid");
    when(subscriptions.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(event);

    useCase.execute(new SubscriptionWebhookCommand("{}", "t=1,v1=abc"));

    assertThat(meters.counter("billing.webhook", "type", "invoice.paid", "result", "ok").count())
        .isEqualTo(1.0);
  }
}
