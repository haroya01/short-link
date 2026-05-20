package com.example.short_link.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.net.Webhook;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class BillingServiceWebhookTest {

  private UserRepository userRepository;
  private SimpleMeterRegistry meters;
  private BillingService service;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    meters = new SimpleMeterRegistry();
    service =
        new BillingService(
            userRepository,
            meters,
            "sk_test_dummy",
            "whsec_test",
            "price_pro",
            "https://success",
            "https://cancel",
            "https://portal");
  }

  @Test
  void handleWebhookDispatchesCheckoutCompletedAndLinksCustomer() {
    com.stripe.model.checkout.Session session =
        Mockito.mock(com.stripe.model.checkout.Session.class);
    when(session.getClientReferenceId()).thenReturn("42");
    when(session.getCustomer()).thenReturn("cus_test_1");
    EventDataObjectDeserializer deserializer = Mockito.mock(EventDataObjectDeserializer.class);
    when(deserializer.getObject()).thenReturn(Optional.of(session));
    Event event = Mockito.mock(Event.class);
    when(event.getType()).thenReturn("checkout.session.completed");
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);

    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-42"));
    Mockito.doReturn(42L).when(user).getId();
    when(userRepository.findById(42L)).thenReturn(Optional.of(user));

    try (MockedStatic<Webhook> hookStatic = mockStatic(Webhook.class)) {
      hookStatic
          .when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
          .thenReturn(event);

      service.handleWebhook("{}", "t=1,v1=abc");
    }

    Mockito.verify(user).linkStripeCustomer("cus_test_1");
    assertThat(
            meters
                .counter("billing.webhook", "type", "checkout.session.completed", "result", "ok")
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void handleWebhookCheckoutCompletedSkipsWhenUserAlreadyHasCustomer() {
    com.stripe.model.checkout.Session session =
        Mockito.mock(com.stripe.model.checkout.Session.class);
    when(session.getClientReferenceId()).thenReturn("99");
    when(session.getCustomer()).thenReturn("cus_test_new");
    EventDataObjectDeserializer deserializer = Mockito.mock(EventDataObjectDeserializer.class);
    when(deserializer.getObject()).thenReturn(Optional.of(session));
    Event event = Mockito.mock(Event.class);
    when(event.getType()).thenReturn("checkout.session.completed");
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);

    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-99"));
    Mockito.doReturn(99L).when(user).getId();
    user.linkStripeCustomer("cus_existing");
    when(userRepository.findById(99L)).thenReturn(Optional.of(user));

    try (MockedStatic<Webhook> hookStatic = mockStatic(Webhook.class)) {
      hookStatic
          .when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
          .thenReturn(event);

      service.handleWebhook("{}", "t=1,v1=abc");
    }

    assertThat(user.getStripeCustomerId()).isEqualTo("cus_existing");
  }

  @Test
  void handleWebhookCheckoutCompletedIgnoresNonNumericClientRef() {
    com.stripe.model.checkout.Session session =
        Mockito.mock(com.stripe.model.checkout.Session.class);
    when(session.getClientReferenceId()).thenReturn("not-a-number");
    when(session.getCustomer()).thenReturn("cus_test");
    EventDataObjectDeserializer deserializer = Mockito.mock(EventDataObjectDeserializer.class);
    when(deserializer.getObject()).thenReturn(Optional.of(session));
    Event event = Mockito.mock(Event.class);
    when(event.getType()).thenReturn("checkout.session.completed");
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);

    try (MockedStatic<Webhook> hookStatic = mockStatic(Webhook.class)) {
      hookStatic
          .when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
          .thenReturn(event);

      service.handleWebhook("{}", "t=1,v1=abc");
    }

    Mockito.verify(userRepository, Mockito.never()).findById(any());
  }

  @Test
  void handleWebhookCheckoutCompletedSkipsWhenSessionIsNull() {
    EventDataObjectDeserializer deserializer = Mockito.mock(EventDataObjectDeserializer.class);
    when(deserializer.getObject()).thenReturn(Optional.empty());
    Event event = Mockito.mock(Event.class);
    when(event.getType()).thenReturn("checkout.session.completed");
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);

    try (MockedStatic<Webhook> hookStatic = mockStatic(Webhook.class)) {
      hookStatic
          .when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
          .thenReturn(event);

      service.handleWebhook("{}", "t=1,v1=abc");
    }

    Mockito.verify(userRepository, Mockito.never()).findById(any());
  }

  @Test
  void handleWebhookSubscriptionUpdatedSetsTierAndPeriod() {
    com.stripe.model.Subscription sub = Mockito.mock(com.stripe.model.Subscription.class);
    when(sub.getCustomer()).thenReturn("cus_active");
    when(sub.getId()).thenReturn("sub_1");
    when(sub.getStatus()).thenReturn("active");
    when(sub.getCurrentPeriodEnd()).thenReturn(1700000000L);
    EventDataObjectDeserializer deserializer = Mockito.mock(EventDataObjectDeserializer.class);
    when(deserializer.getObject()).thenReturn(Optional.of(sub));
    Event event = Mockito.mock(Event.class);
    when(event.getType()).thenReturn("customer.subscription.updated");
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);

    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-sub1"));
    user.linkStripeCustomer("cus_active");
    when(userRepository.findByStripeCustomerId("cus_active")).thenReturn(Optional.of(user));

    try (MockedStatic<Webhook> hookStatic = mockStatic(Webhook.class)) {
      hookStatic
          .when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
          .thenReturn(event);

      service.handleWebhook("{}", "t=1,v1=abc");
    }

    Mockito.verify(user).applySubscription(Mockito.eq("sub_1"), Mockito.eq("active"), any());
  }

  @Test
  void handleWebhookSubscriptionDeletedClearsSubscription() {
    com.stripe.model.Subscription sub = Mockito.mock(com.stripe.model.Subscription.class);
    when(sub.getCustomer()).thenReturn("cus_del");
    EventDataObjectDeserializer deserializer = Mockito.mock(EventDataObjectDeserializer.class);
    when(deserializer.getObject()).thenReturn(Optional.of(sub));
    Event event = Mockito.mock(Event.class);
    when(event.getType()).thenReturn("customer.subscription.deleted");
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);

    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-sub2"));
    user.linkStripeCustomer("cus_del");
    when(userRepository.findByStripeCustomerId("cus_del")).thenReturn(Optional.of(user));

    try (MockedStatic<Webhook> hookStatic = mockStatic(Webhook.class)) {
      hookStatic
          .when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
          .thenReturn(event);

      service.handleWebhook("{}", "t=1,v1=abc");
    }

    Mockito.verify(user).clearSubscription();
  }

  @Test
  void handleWebhookSubscriptionSkipsWhenSubscriptionIsNull() {
    EventDataObjectDeserializer deserializer = Mockito.mock(EventDataObjectDeserializer.class);
    when(deserializer.getObject()).thenReturn(Optional.empty());
    Event event = Mockito.mock(Event.class);
    when(event.getType()).thenReturn("customer.subscription.updated");
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);

    try (MockedStatic<Webhook> hookStatic = mockStatic(Webhook.class)) {
      hookStatic
          .when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
          .thenReturn(event);

      service.handleWebhook("{}", "t=1,v1=abc");
    }

    Mockito.verify(userRepository, Mockito.never()).findByStripeCustomerId(anyString());
  }

  @Test
  void handleWebhookSubscriptionSkipsWhenCustomerUnknown() {
    com.stripe.model.Subscription sub = Mockito.mock(com.stripe.model.Subscription.class);
    when(sub.getCustomer()).thenReturn("cus_ghost");
    EventDataObjectDeserializer deserializer = Mockito.mock(EventDataObjectDeserializer.class);
    when(deserializer.getObject()).thenReturn(Optional.of(sub));
    Event event = Mockito.mock(Event.class);
    when(event.getType()).thenReturn("customer.subscription.updated");
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);
    when(userRepository.findByStripeCustomerId("cus_ghost")).thenReturn(Optional.empty());

    try (MockedStatic<Webhook> hookStatic = mockStatic(Webhook.class)) {
      hookStatic
          .when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
          .thenReturn(event);

      service.handleWebhook("{}", "t=1,v1=abc");
    }
    // No exception, no user write — log-only path
  }

  @Test
  void handleWebhookUnknownTypeIgnored() {
    Event event = Mockito.mock(Event.class);
    when(event.getType()).thenReturn("invoice.paid");

    try (MockedStatic<Webhook> hookStatic = mockStatic(Webhook.class)) {
      hookStatic
          .when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
          .thenReturn(event);

      service.handleWebhook("{}", "t=1,v1=abc");
    }

    assertThat(meters.counter("billing.webhook", "type", "invoice.paid", "result", "ok").count())
        .isEqualTo(1.0);
  }
}
